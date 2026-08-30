#!/usr/bin/env python3
"""Quran-content identity diagnostic using a public int8 Wav2Vec2 CTC model.

This layer answers only whether the spoken text matches the expected ayah/surah.
It MUST NOT be used as the Tajweed judge. The Tajweed engine remains separate.

V2.2 deliberately does not trust free-running ASR text alone. It also scores the
known expected Quran text directly against the CTC lattice using:
  * summed CTC forward likelihood
  * forced Viterbi path vs. the unconstrained framewise best path

Arabic harakat/Quran pause marks are treated as optional blank-like emissions for
the content-identity score. This lets the gate verify the letter/word sequence
without pretending that this ASR model is itself a Tajweed judge.
"""
from __future__ import annotations
import argparse, json, math, re, unicodedata, wave
from difflib import SequenceMatcher
from pathlib import Path

import numpy as np
import onnxruntime as ort

DIACRITICS = re.compile(r"[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED]")
SPECIAL = {"<pad>", "[PAD]", "<s>", "</s>", "[CLS]", "[SEP]", "[MASK]"}
UNKNOWNS = {"<unk>", "[UNK]"}
OPTIONAL_MARKS = set("ًٌٍَُِّْٰٓۖۗۘۙۚۛۜ۩ـ")
NEG_INF = -1.0e30


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
    return vocab, {int(v): k for k, v in vocab.items()}


def log_softmax(x: np.ndarray) -> np.ndarray:
    m = np.max(x, axis=-1, keepdims=True)
    z = np.log(np.sum(np.exp(x - m), axis=-1, keepdims=True))
    return x - m - z


def logadd(values) -> float:
    vals = [float(v) for v in values if float(v) > NEG_INF / 2]
    if not vals:
        return NEG_INF
    m = max(vals)
    return m + math.log(sum(math.exp(v - m) for v in vals))


def ctc_decode(ids, id_to_token):
    out = []
    prev = None
    for raw in ids:
        i = int(raw)
        if i == prev:
            continue
        prev = i
        token = id_to_token.get(i, "")
        if not token or token in SPECIAL:
            continue
        if token in UNKNOWNS or token == "|":
            out.append(" ")
        else:
            out.append(token)
    return re.sub(r"\s+", " ", "".join(out)).strip()


def normalize_ar(s: str, strip_diacritics: bool = True) -> str:
    s = unicodedata.normalize("NFKC", s).replace("ٱ", "ا").replace("ـ", "")
    if strip_diacritics:
        s = DIACRITICS.sub("", s)
    s = re.sub(r"[^ء-يآأإؤئاةى \u064B-\u065F\u0670\u06D6-\u06ED]", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def canonical_target(expected: str, vocab: dict[str, int]):
    # Identity uses letters + spaces. Harakat/Quran marks are optional emissions,
    # not required target labels, so legitimate riwaya/orthography variation does
    # not turn into a false wrong-surah decision.
    text = normalize_ar(expected, strip_diacritics=True)
    chars = []
    ids = []
    missing = []
    for ch in text:
        token = ch
        if token in vocab:
            chars.append(token); ids.append(int(vocab[token])); continue
        fallback = {"ٱ": "ا"}.get(token, token)
        if fallback in vocab:
            chars.append(fallback); ids.append(int(vocab[fallback])); continue
        missing.append(token)
    return "".join(chars), ids, sorted(set(missing))


def blank_like_ids(vocab: dict[str, int]):
    ids = []
    tokens = []
    for token, idx in vocab.items():
        if token in SPECIAL or token in OPTIONAL_MARKS:
            ids.append(int(idx)); tokens.append(token)
    pad = vocab.get("[PAD]", vocab.get("<pad>"))
    if pad is not None and int(pad) not in ids:
        ids.append(int(pad)); tokens.append("[PAD]")
    return sorted(set(ids)), tokens


def ctc_forced_score(logits: np.ndarray, expected: str, vocab: dict[str, int]):
    lp = log_softmax(logits.astype(np.float64))
    target_text, target, missing = canonical_target(expected, vocab)
    blank_ids, blank_tokens = blank_like_ids(vocab)
    if not target:
        raise ValueError("Expected text produced no target tokens")
    if lp.shape[0] < len(target):
        return {
            "target_text": target_text,
            "target_tokens": len(target),
            "frames": int(lp.shape[0]),
            "missing_target_chars": missing,
            "blank_like_ids": blank_ids,
            "blank_like_tokens": blank_tokens,
            "possible": False,
            "reason": "audio_too_short_for_target",
        }

    # Marginalize over blank + optional Arabic harakat/Quran marks.
    blank_lp = np.empty(lp.shape[0], dtype=np.float64)
    for t in range(lp.shape[0]):
        blank_lp[t] = logadd(lp[t, blank_ids])

    # Extended CTC state sequence: blank, y1, blank, y2, ... blank.
    states = []
    for token_id in target:
        states.append(None)
        states.append(token_id)
    states.append(None)
    S = len(states)

    fwd = np.full(S, NEG_INF, dtype=np.float64)
    vit = np.full(S, NEG_INF, dtype=np.float64)
    fwd[0] = blank_lp[0]; vit[0] = blank_lp[0]
    if S > 1:
        fwd[1] = lp[0, states[1]]; vit[1] = lp[0, states[1]]

    for t in range(1, lp.shape[0]):
        nf = np.full(S, NEG_INF, dtype=np.float64)
        nv = np.full(S, NEG_INF, dtype=np.float64)
        for s, label in enumerate(states):
            prevs = [s]
            if s > 0:
                prevs.append(s - 1)
            if label is not None and s > 1:
                prev_label = states[s - 2]
                if prev_label is not None and prev_label != label:
                    prevs.append(s - 2)
            emit = blank_lp[t] if label is None else lp[t, label]
            nf[s] = logadd(fwd[p] for p in prevs) + emit
            nv[s] = max(vit[p] for p in prevs) + emit
        fwd, vit = nf, nv

    finals = [S - 1]
    if S > 1:
        finals.append(S - 2)
    total_ll = logadd(fwd[s] for s in finals)
    forced_vit = max(float(vit[s]) for s in finals)
    free_best = float(np.max(lp, axis=1).sum())
    frames = int(lp.shape[0]); n_target = len(target)

    return {
        "target_text": target_text,
        "target_tokens": n_target,
        "frames": frames,
        "missing_target_chars": missing,
        "blank_like_ids": blank_ids,
        "blank_like_tokens": blank_tokens,
        "possible": True,
        "ctc_log_likelihood": round(float(total_ll), 3),
        "ctc_nll_per_target": round(float(-total_ll / n_target), 5),
        "ctc_nll_per_frame": round(float(-total_ll / frames), 6),
        "forced_viterbi_logprob": round(forced_vit, 3),
        "free_best_logprob": round(free_best, 3),
        "viterbi_gap_per_frame": round(float((free_best - forced_vit) / frames), 6),
        "viterbi_gap_per_target": round(float((free_best - forced_vit) / n_target), 5),
    }


def transcribe(model_path: str, vocab_path: str, wav_path: str, expected: str = ""):
    x = read_wav_16k(wav_path)
    so = ort.SessionOptions(); so.intra_op_num_threads = 4; so.inter_op_num_threads = 1
    session = ort.InferenceSession(model_path, sess_options=so, providers=["CPUExecutionProvider"])
    inp = session.get_inputs()[0]
    logits = session.run(None, {inp.name: x[None, :]})[0]
    ids = np.argmax(logits[0], axis=-1)
    vocab, id_to_token = load_vocab(vocab_path)
    text = ctc_decode(ids, id_to_token)
    blank_candidates = {k: v for k, v in vocab.items() if k in SPECIAL}
    result = {
        "input_name": inp.name,
        "input_shape": [str(v) for v in inp.shape],
        "output_shape": list(logits.shape),
        "duration_ms": round(x.size * 1000 / 16000),
        "blank_candidates": blank_candidates,
        "transcript": text,
    }
    if expected:
        result["forced_identity"] = ctc_forced_score(logits[0], expected, vocab)
    return result


def similarity(transcript: str, expected: str):
    na = normalize_ar(transcript, strip_diacritics=True); nb = normalize_ar(expected, strip_diacritics=True)
    a = na.replace(" ", ""); b = nb.replace(" ", "")
    ratio = SequenceMatcher(None, a, b, autojunk=False).ratio() if a and b else 0.0
    matcher = SequenceMatcher(None, a, b, autojunk=False)
    matched = sum(m.size for m in matcher.get_matching_blocks())
    return {
        "normalized_transcript": na,
        "normalized_expected": nb,
        "char_similarity": round(ratio, 4),
        "expected_char_coverage": round(matched / max(1, len(b)), 4),
        "transcript_chars": len(a),
        "expected_chars": len(b),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("model"); ap.add_argument("vocab"); ap.add_argument("wav"); ap.add_argument("--expected", default="")
    args = ap.parse_args()
    result = transcribe(args.model, args.vocab, args.wav, args.expected)
    if args.expected:
        result["identity"] = similarity(result["transcript"], args.expected)
    print(json.dumps(result, ensure_ascii=False, indent=2))

if __name__ == "__main__": main()
