#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets"
MODEL="$ASSETS/quran_content_gate_v22.onnx"
VOCAB="$ASSETS/quran_content_gate_vocab.json"
MODEL_SHA256='dc7373e31802a8691dd546a8883e3f330e33a01842e0e1121442ebe71601fdbc'
MODEL_URL='https://huggingface.co/Tidzo/darten-quran-asr/resolve/main/model.hamza.int8.onnx?download=true'
VOCAB_URL='https://huggingface.co/HamzaSidhu786/wav2vec2-base-word-by-word-quran-asr/resolve/main/vocab.json?download=true'

mkdir -p "$ASSETS"

download() {
  local url="$1" out="$2"
  local tmp="${out}.part"
  rm -f "$tmp"
  curl --fail --location --retry 4 --retry-delay 2 "$url" -o "$tmp"
  mv "$tmp" "$out"
}

if [[ ! -s "$MODEL" ]] || ! echo "$MODEL_SHA256  $MODEL" | sha256sum -c - >/dev/null 2>&1; then
  echo 'Fetching pinned Quran CTC model...'
  download "$MODEL_URL" "$MODEL"
fi

echo "$MODEL_SHA256  $MODEL" | sha256sum -c -
test "$(stat -c%s "$MODEL")" -gt 100000000

if [[ ! -s "$VOCAB" ]]; then
  echo 'Fetching pinned Quran CTC vocabulary...'
  download "$VOCAB_URL" "$VOCAB"
fi

python3 - "$VOCAB" <<'PY'
import json, sys
p=sys.argv[1]
v=json.load(open(p,encoding='utf-8'))
assert v.get('[PAD]') == 0, v.get('[PAD]')
assert len(v) >= 60, len(v)
print('CONTENT_GATE_VOCAB_OK tokens=', len(v))
PY

echo 'CONTENT_GATE_MODEL_READY'
