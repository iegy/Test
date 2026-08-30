#!/usr/bin/env python3
"""Run the Maher cross-reciter fixture with the proposed v2.1 robust VAD.

This file is intentionally small and independent: the CI gate proves the VAD
change on a real second reciter before the same policy is promoted to Android.
"""
from __future__ import annotations
import json, math, sys
import reference_pipeline as rp


def robust_detect_speech(frames):
    if not frames:
        return {"segments": [], "speech_ms": 0.0, "snr_db": 0.0}
    vals = sorted(f.rms for f in frames)
    noise = vals[min(len(vals)-1, int(len(vals)*.20))]
    p80 = vals[min(len(vals)-1, int(len(vals)*.80))]
    # A single loud syllable/echo must not raise the threshold for the whole ayah.
    threshold = max(noise * 1.60, p80 * .28)
    for f in frames:
        f.active = f.rms >= threshold

    # Bridge tiny energy gaps (<=40 ms) caused by consonants or room response.
    i = 0
    while i < len(frames):
        if frames[i].active:
            i += 1
            continue
        start = i
        while i < len(frames) and not frames[i].active:
            i += 1
        if start > 0 and i < len(frames) and i - start <= 4:
            for j in range(start, i):
                frames[j].active = True

    segments = []
    i = 0
    while i < len(frames):
        if not frames[i].active:
            i += 1
            continue
        start_ms = frames[i].start_ms
        j = i
        gap = 0
        while j + 1 < len(frames):
            j += 1
            if frames[j].active:
                gap = 0
            else:
                gap += 1
            if gap > 18:
                j -= gap
                break
        if frames[j].end_ms - start_ms >= 90:
            segments.append((start_ms, frames[j].end_ms))
        i = max(i + 1, j + gap + 1)

    speech_ms = sum(b-a for a,b in segments)
    active_rms = [f.rms for f in frames if f.active]
    signal = sum(active_rms)/len(active_rms) if active_rms else 0.0
    snr = 20 * math.log10(max(1e-7, signal) / max(1e-7, noise))
    return {"segments": segments, "speech_ms": speech_ms, "snr_db": snr}


# Python functions resolve detect_speech from their module globals at runtime.
rp.detect_speech = robust_detect_speech

if len(sys.argv) != 3:
    raise SystemExit("usage: maher_cross_reciter_v21.py MINShAWI.wav MAHER.wav")

result = rp.compare_maher_fixture(sys.argv[1], sys.argv[2])
print(json.dumps(result, ensure_ascii=False, indent=2))
