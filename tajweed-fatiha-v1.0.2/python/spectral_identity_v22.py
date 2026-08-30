#!/usr/bin/env python3
"""Lightweight text-identity fingerprint for v2.2.

Uses 13 fixed spectral probes, per-utterance normalization and temporal deltas.
It is intentionally a CONTENT gate only. It does not score Tajweed rules.
The math is chosen so the same implementation can be ported to Kotlin without
shipping a third-party ASR model.
"""
from __future__ import annotations
import argparse, json, math
import reference_pipeline as rp

FREQS = [220, 320, 450, 630, 880, 1200, 1600, 2100, 2700, 3400, 4300, 5400, 6600]


def robust_activity(a: rp.Audio):
    p = rp.preprocess(a)
    frames = rp.extract_frames(p)
    if not frames:
        return p, frames
    vals = sorted(f.rms for f in frames)
    noise = vals[min(len(vals)-1, int(len(vals)*.20))]
    p80 = vals[min(len(vals)-1, int(len(vals)*.80))]
    th = max(noise * 1.60, p80 * .28)
    for f in frames:
        f.active = f.rms >= th
    # bridge <=40 ms internal gaps
    i = 0
    while i < len(frames):
        if frames[i].active:
            i += 1; continue
        st = i
        while i < len(frames) and not frames[i].active: i += 1
        if st > 0 and i < len(frames) and i-st <= 4:
            for j in range(st, i): frames[j].active = True
    return p, frames


def goertzel_power(frame, sr, freq):
    n = len(frame)
    if n < 4: return -30.0
    k = int(0.5 + n * freq / sr)
    w = 2.0 * math.pi * k / n
    coeff = 2.0 * math.cos(w)
    s1 = s2 = 0.0
    denom = max(1, n-1)
    for i, sample in enumerate(frame):
        x = sample * (0.54 - 0.46 * math.cos(2.0 * math.pi * i / denom))
        s0 = x + coeff * s1 - s2
        s2, s1 = s1, s0
    power = s1*s1 + s2*s2 - coeff*s1*s2
    return math.log(max(1e-12, power))


def fingerprint(a: rp.Audio):
    p, activity = robust_activity(a)
    if not activity: return []
    sr = p.rate
    frame_n = max(128, int(sr * .025))
    hop = max(64, int(sr * .020))
    rows = []
    for start in range(0, max(1, len(p.samples)-frame_n+1), hop):
        ms = start * 1000.0 / sr
        ai = min(len(activity)-1, max(0, int(ms / 10.0)))
        if not activity[ai].active: continue
        y = p.samples[start:start+frame_n]
        bands = [goertzel_power(y, sr, f) for f in FREQS]
        local_mean = sum(bands) / len(bands)
        rows.append([v-local_mean for v in bands])
    if len(rows) < 5: return rows

    dims = len(rows[0])
    means = [sum(r[j] for r in rows)/len(rows) for j in range(dims)]
    stds = []
    for j in range(dims):
        var = sum((r[j]-means[j])**2 for r in rows)/len(rows)
        stds.append(max(.25, math.sqrt(var)))
    norm = [[max(-4.0, min(4.0, (r[j]-means[j])/stds[j])) for j in range(dims)] for r in rows]

    out = []
    prev = norm[0]
    for r in norm:
        delta = [(r[j]-prev[j]) * .45 for j in range(dims)]
        out.append(r + delta)
        prev = r
    if len(out) > 1000:
        step = len(out)//1000 + 1
        out = out[::step]
    return out


def distance(a, b):
    if not a or not b: return 3.0
    return sum(min(3.0, abs(x-y)) for x,y in zip(a,b)) / len(a)


def subsequence_cost(ref, obs):
    if not ref or not obs: return 99.0
    n, m = len(ref), len(obs)
    inf = 1e18
    prev = [0.0] * (m+1)  # free candidate prefix
    for i in range(1, n+1):
        cur = [inf] * (m+1)
        for j in range(1, m+1):
            d = distance(ref[i-1], obs[j-1])
            cur[j] = d + min(prev[j-1], prev[j] + .12, cur[j-1] + .12)
        prev = cur
    return min(prev[1:]) / max(1, n)


def compare(reference_path, candidate_path):
    r = rp.read_wav(reference_path)
    c = rp.read_wav(candidate_path)
    rf = fingerprint(r)
    cf = fingerprint(c)
    return {
        "model": "spectral-content-identity-v2.2-goertzel13-cmvn-delta",
        "reference_frames": len(rf),
        "candidate_frames": len(cf),
        "identity_cost": round(subsequence_cost(rf, cf), 4),
        "candidate_duration_ms": round(len(c.samples)*1000/c.rate),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("reference_wav")
    ap.add_argument("candidate_wav")
    args = ap.parse_args()
    print(json.dumps(compare(args.reference_wav, args.candidate_wav), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
