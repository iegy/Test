#!/usr/bin/env python3
"""Al-Fatiha v2.2 content-identity and hierarchical alignment gate.

The gate answers one question only: does the candidate contain the expected
Al-Fatiha ayat in order strongly enough to proceed to tajweed diagnostics?
It is NOT a tajweed judge and it is intentionally independent from word-level
feedback. A negative gate produces no word colours.
"""
from __future__ import annotations
import argparse, json, math
import reference_pipeline as rp


def robust_detect_speech(frames):
    if not frames:
        return {"segments": [], "speech_ms": 0.0, "snr_db": 0.0}
    vals = sorted(f.rms for f in frames)
    noise = vals[min(len(vals)-1, int(len(vals)*.20))]
    p80 = vals[min(len(vals)-1, int(len(vals)*.80))]
    threshold = max(noise * 1.60, p80 * .28)
    for f in frames:
        f.active = f.rms >= threshold
    i = 0
    while i < len(frames):
        if frames[i].active:
            i += 1
            continue
        st = i
        while i < len(frames) and not frames[i].active:
            i += 1
        if st > 0 and i < len(frames) and i - st <= 4:
            for j in range(st, i):
                frames[j].active = True
    segs = []
    i = 0
    while i < len(frames):
        if not frames[i].active:
            i += 1
            continue
        st = frames[i].start_ms
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
        if frames[j].end_ms - st >= 90:
            segs.append((st, frames[j].end_ms))
        i = max(i + 1, j + gap + 1)
    speech_ms = sum(b-a for a,b in segs)
    active_rms = [f.rms for f in frames if f.active]
    signal = sum(active_rms)/len(active_rms) if active_rms else 0.0
    snr = 20 * math.log10(max(1e-7, signal) / max(1e-7, noise))
    return {"segments": segs, "speech_ms": speech_ms, "snr_db": snr}


rp.detect_speech = robust_detect_speech


def normalized_active(a: rp.Audio):
    p = rp.preprocess(a)
    fr = rp.extract_frames(p)
    v = rp.detect_speech(fr)
    return p, fr, v, rp.compact(rp.normalize_for_alignment(fr))


def gate(reference_path: str, candidate_path: str):
    ref = rp.read_wav(reference_path)
    cand = rp.read_wav(candidate_path)
    rr = rp.raw_ayah_ranges(ref)

    ref_concat = []
    ref_boundaries = [0]
    for idx, r in enumerate(rr, 1):
        a = rp.slice_audio(ref, r)
        _, _, _, an = normalized_active(a)
        if len(an) < 4:
            raise ValueError(f"Reference ayah {idx} has insufficient speech")
        ref_concat.extend(an)
        ref_boundaries.append(len(ref_concat))

    _, _, cv, cn = normalized_active(cand)
    if len(cn) < 4:
        return {"accepted": False, "reason": "NO_SPEECH", "global_cost": 99.0, "matched_ayah_count": 0, "valid_ayah_count": 0, "rows": []}

    global_cost, path = rp.subsequence_dtw_path(ref_concat, cn)
    if not path:
        return {"accepted": False, "reason": "NO_PATH", "global_cost": 99.0, "matched_ayah_count": 0, "valid_ayah_count": 0, "rows": []}

    mapped = []
    for bi in ref_boundaries:
        target = min(max(0, bi), max(0, len(ref_concat)-1))
        choices = [p for p in path if p[0] >= target]
        chosen = choices[0] if choices else path[-1]
        mapped.append(cn[chosen[1]].start_ms)
    mapped[-1] = cn[path[-1][1]].end_ms

    ranges = []
    monotonic = True
    for i in range(7):
        st = max(0.0, mapped[i] - 120.0)
        en = min(len(cand.samples)*1000/cand.rate, mapped[i+1] + 120.0)
        if en <= st + 350.0:
            monotonic = False
        ranges.append((st, en))

    rows = []
    for i, (rs, cr) in enumerate(zip(rr, ranges), 1):
        ra = rp.slice_audio(ref, rs)
        ca = rp.slice_audio(cand, cr)
        q = rp.validate_audio(ca)
        al = rp.speaker_alignment(ra, ca)
        ref_q = rp.validate_audio(ra)
        tempo = q["speech_ms"] / max(1, ref_q["speech_ms"])
        rows.append({
            "ayah": i,
            "candidate_range_ms": [round(cr[0]), round(cr[1])],
            "candidate_duration_ms": round(cr[1]-cr[0]),
            "quality": q,
            "alignment_cost": al["cost"],
            "alignment_accepted": al["accepted"],
            "speech_ratio_to_reference": round(tempo, 3),
            "boundary_sane": (cr[1]-cr[0]) >= 500.0,
        })

    aligned_count = sum(r["alignment_cost"] < .93 for r in rows)
    strong_count = sum(r["alignment_cost"] < .82 for r in rows)
    valid_count = sum(bool(r["quality"]["accepted"]) for r in rows)
    sane_count = sum(bool(r["boundary_sane"]) for r in rows)
    mean_cost = sum(r["alignment_cost"] for r in rows) / len(rows)
    p90_cost = sorted(r["alignment_cost"] for r in rows)[-2]

    path_start = cn[path[0][1]].start_ms
    path_end = cn[path[-1][1]].end_ms
    cand_speech_start = cv["segments"][0][0] if cv["segments"] else 0.0
    cand_speech_end = cv["segments"][-1][1] if cv["segments"] else len(cand.samples)*1000/cand.rate
    speech_span = max(1.0, cand_speech_end - cand_speech_start)
    matched_span_ratio = max(0.0, path_end - path_start) / speech_span

    accepted = (
        monotonic
        and global_cost < .78
        and mean_cost < .82
        and p90_cost < .93
        and aligned_count >= 6
        and strong_count >= 4
        and valid_count >= 6
        and sane_count == 7
        and matched_span_ratio >= .62
    )
    return {
        "accepted": accepted,
        "reason": "PASS" if accepted else "CONTENT_MISMATCH",
        "global_cost": round(global_cost, 3),
        "mean_ayah_cost": round(mean_cost, 3),
        "p90_ayah_cost": round(p90_cost, 3),
        "matched_ayah_count": aligned_count,
        "strong_ayah_count": strong_count,
        "valid_ayah_count": valid_count,
        "sane_boundary_count": sane_count,
        "matched_span_ratio": round(matched_span_ratio, 3),
        "candidate_speech_ms": round(cv["speech_ms"]),
        "candidate_snr_db": round(cv["snr_db"], 1),
        "path_range_ms": [round(path_start), round(path_end)],
        "rows": rows,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("reference_wav")
    ap.add_argument("candidate_wav")
    ap.add_argument("--expect", choices=["accept", "reject"])
    args = ap.parse_args()
    result = gate(args.reference_wav, args.candidate_wav)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if args.expect == "accept" and not result["accepted"]:
        raise SystemExit(2)
    if args.expect == "reject" and result["accepted"]:
        raise SystemExit(3)


if __name__ == "__main__":
    main()
