#!/usr/bin/env python3
"""Quran-content identity diagnostic using a small public int8 Wav2Vec2 CTC model.

This layer answers only whether the spoken text matches the expected ayah/surah.
It MUST NOT be used as the Tajweed judge. The Tajweed engine remains separate.

Model tested by CI:
  Tidzo/darten-quran-asr model.hamza.int8.onnx (Apache-2.0 mirror/export)
Vocabulary/preprocessing:
  HamzaSidhu786/wav2vec2-base-word-by-word-quran-asr (Apache-2.0)
"""
from __future__ import annotations
import argparse, json, math, re, unicodedata, wave
from difflib import SequenceMatcher
from pathlib import Path

import numpy as np
import onnxruntime as ort

DIACRITICS = re.compile(r"[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED]")


def read_wav_16k(path: str) -> np.ndarray:
    with wave.open(path, "rb") as w:
        ch, sw, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        if sw != 2 or sr != 16000:
            raise ValueError(f"Expected 16-bit PCM 16kHz WAV, got sw={sw} sr={sr}")
        raw = w.readframes(n)
    x = np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768.0
    if ch > 1:
        x = x.reshape(-1, ch).mean(axis=1)
    if x.size:
        x = x - x.mean()
        std = float(x.std())
        if std > 1e-7:
            x = x / std
    return x.astype(np.float32)


def load_vocab(path: str):
    vocab = json.loads(Path(path).read_text(encoding="utf-8"))
    return {int(v): k for k, v in vocab.items()}


def ctc_decode(ids, id_to_token):
    out = []
    prev = None
    for raw in ids:
        i = int(raw)
        if i == prev:
            continue
        prev = i
        token = id_to_token.get(i, "")
        if not token or token in {"<pad>", "<s>", "</s>"}:
            continue
        if token == "<unk>":
            out.append(" ")
        elif token == "|":
            out.append(" ")
        else:
            out.append(token)
    return re.sub(r"\s+", " ", "".join(out)).strip()


def transcribe(model_path: str, vocab_path: str, wav_path: str):
    x = read_wav_16k(wav_path)
    so = ort.SessionOptions()
    so.intra_op_num_threads = 4
    so.inter_op_num_threads = 1
    session = ort.InferenceSession(model_path, sess_options=so, providers=["CPUExecutionProvider"])
    inp = session.get_inputs()[0]
    outputs = session.run(None, {inp.name: x[None, :]})
    logits = outputs[0]
    ids = np.argmax(logits[0], axis=-1)
    text = ctc_decode(ids, load_vocab(vocab_path))
    return {
        "input_name": inp.name,
        "input_shape": [str(v) for v in inp.shape],
        "output_shape": list(logits.shape),
        "duration_ms": round(x.size * 1000 / 16000),
        "transcript": text,
    }


def normalize_ar(s: str) -> str:
    s = unicodedata.normalize("NFKC", s)
    s = DIACRITICS.sub("", s).replace("ـ", "")
    s = s.replace("ٱ", "ا").replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
    s = s.replace("ى", "ي").replace("ؤ", "و").replace("ئ", "ي")
    s = re.sub(r"[^ء-ي ]", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def similarity(transcript: str, expected: str):
    a = normalize_ar(transcript).replace(" ", "")
    b = normalize_ar(expected).replace(" ", "")
    ratio = SequenceMatcher(None, a, b, autojunk=False).ratio() if a and b else 0.0
    return {
        "normalized_transcript": normalize_ar(transcript),
        "normalized_expected": normalize_ar(expected),
        "char_similarity": round(ratio, 4),
        "transcript_chars": len(a),
        "expected_chars": len(b),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("model")
    ap.add_argument("vocab")
    ap.add_argument("wav")
    ap.add_argument("--expected", default="")
    args = ap.parse_args()
    result = transcribe(args.model, args.vocab, args.wav)
    if args.expected:
        result["identity"] = similarity(result["transcript"], args.expected)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
