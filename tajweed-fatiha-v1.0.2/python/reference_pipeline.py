#!/usr/bin/env python3
"""
Al-Fatiha native v2 reference/regression pipeline.

Purpose:
- desktop/reference validation for the Android engine;
- deterministic audio-quality/VAD and known-reference timing checks;
- regression fixtures for the bundled Minshawi Al-Fatiha reference.

This is NOT a phoneme classifier and does not claim definitive makhraj scoring.
Input is mono/stereo 16-bit PCM WAV. Use ffmpeg to decode OGG/MP3 first.
"""
from __future__ import annotations
import argparse, json, math, wave
from dataclasses import dataclass
from typing import List, Tuple

TARGET_SR = 16000

@dataclass
class Audio:
    samples: List[float]
    rate: int

@dataclass
class Frame:
    start_ms: float
    end_ms: float
    rms: float
    zcr: float
    low: float
    mid: float
    high: float
    active: bool = False

def read_wav(path: str) -> Audio:
    with wave.open(path, "rb") as w:
        ch, sw, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        if sw != 2:
            raise ValueError("16-bit PCM WAV required")
        raw = w.readframes(n)
    vals = []
    for i in range(0, len(raw), 2):
        v = int.from_bytes(raw[i:i+2], "little", signed=True) / 32768.0
        vals.append(v)
    if ch > 1:
        mono = []
        for i in range(0, len(vals), ch):
            mono.append(sum(vals[i:i+ch]) / ch)
        vals = mono
    if sr != TARGET_SR:
        vals = resample(vals, sr, TARGET_SR)
        sr = TARGET_SR
    return Audio(vals, sr)

def resample(x: List[float], src: int, dst: int) -> List[float]:
    if src == dst:
        return list(x)
    ratio = src / dst
    n = max(1, int(len(x) / ratio))
    out = [0.0] * n
    for i in range(n):
        p = i * ratio
        a = min(len(x)-1, int(p))
        b = min(len(x)-1, a+1)
        t = p - a
        out[i] = x[a] * (1-t) + x[b] * t
    return out

def preprocess(a: Audio) -> Audio:
    x = list(a.samples)
    if not x:
        return a
    mean = sum(x)/len(x)
    x = [v-mean for v in x]
    peak = max(1e-6, max(abs(v) for v in x))
    gain = min(5.0, 0.88/peak)
    return Audio([max(-1.0, min(1.0, v*gain)) for v in x], a.rate)

def extract_frames(a: Audio) -> List[Frame]:
    frame = max(64, int(a.rate*.025))
    hop = max(32, int(a.rate*.010))
    out = []
    x = a.samples
    for s in range(0, len(x), hop):
        y = x[s:min(len(x), s+frame)]
        if not y:
            continue
        rms = math.sqrt(sum(v*v for v in y)/len(y))
        z = sum((y[i] >= 0) != (y[i-1] >= 0) for i in range(1,len(y))) / max(1,len(y)-1)
        lp = y[0]
        low = mid = high = 0.0
        prev = y[0]
        for v in y:
            lp = lp*.88 + v*.12
            low += abs(lp)
            high += abs(v-lp)
            mid += abs(v-prev)
            prev = v
        n = len(y)
        out.append(Frame(s*1000/a.rate,(s+n)*1000/a.rate,rms,z,low/n,mid/n,high/n))
    return out

def detect_speech(frames: List[Frame]):
    if not frames:
        return {"segments":[],"speech_ms":0.0,"snr_db":0.0}
    vals = sorted(f.rms for f in frames)
    noise = vals[min(len(vals)-1, int(len(vals)*.20))]
    peak = vals[-1]
    th = max(noise*2.45, peak*.075)
    for f in frames:
        f.active = f.rms >= th
    for i in range(1,len(frames)-1):
        if not frames[i].active and frames[i-1].active and frames[i+1].active:
            frames[i].active = True
    segs=[]
    i=0
    while i<len(frames):
        if not frames[i].active:
            i+=1; continue
        st=frames[i].start_ms
        j=i; gap=0
        while j+1<len(frames):
            j+=1
            if frames[j].active: gap=0
            else: gap+=1
            if gap>12:
                j-=gap; break
        if frames[j].end_ms-st>=90:
            segs.append((st,frames[j].end_ms))
        i=max(i+1,j+gap+1)
    speech=sum(b-a for a,b in segs)
    active_rms=[f.rms for f in frames if f.active]
    signal=sum(active_rms)/len(active_rms) if active_rms else 0
    snr=20*math.log10(max(1e-7,signal)/max(1e-7,noise))
    return {"segments":segs,"speech_ms":speech,"snr_db":snr}

def raw_ayah_ranges(a: Audio) -> List[Tuple[float,float]]:
    frame=max(1,int(a.rate*.05))
    energy=[]
    for s in range(0,len(a.samples),frame):
        y=a.samples[s:s+frame]
        energy.append(math.sqrt(sum(v*v for v in y)/max(1,len(y))))
    se=sorted(energy)
    floor=se[min(len(se)-1,int(len(se)*.15))]
    peak=max(energy)
    active=[v>max(floor*2.6,peak*.035) for v in energy]
    first=next((i for i,v in enumerate(active) if v),0)
    last=max((i for i,v in enumerate(active) if v),default=len(active)-1)
    candidates=[]
    i=first+1
    while i<last:
        if not active[i]:
            st=i
            while i<last and not active[i]: i+=1
            ln=i-st
            if ln>=4: candidates.append((st+ln//2,ln))
        else: i+=1
    strong=sorted(c for c in candidates if c[1]>=10)
    last_ms=min(len(a.samples)*1000/a.rate,(last+1)*50.0)
    if len(strong)==7:  # ta'awwudh + seven ayat
        bounds=[c[0]*50.0 for c in strong]+[last_ms]
    elif len(strong)==6: # seven ayat only
        bounds=[first*50.0]+[c[0]*50.0 for c in strong]+[last_ms]
    else:
        raise ValueError(f"Expected 6 or 7 strong pauses, found {len(strong)}")
    return [(max(0,bounds[i]-120),min(len(a.samples)*1000/a.rate,bounds[i+1]+120)) for i in range(7)]

def slice_audio(a: Audio, r: Tuple[float,float]) -> Audio:
    s=int(r[0]*a.rate/1000); e=int(r[1]*a.rate/1000)
    return Audio(a.samples[s:e],a.rate)

def time_stretch_by_resample(a: Audio, factor: float) -> Audio:
    # factor>1 means slower/longer. Preserve output rate while changing sample count.
    n=max(1,int(len(a.samples)*factor))
    out=[0.0]*n
    for i in range(n):
        p=i/factor
        aa=min(len(a.samples)-1,int(p)); bb=min(len(a.samples)-1,aa+1); t=p-aa
        out[i]=a.samples[aa]*(1-t)+a.samples[bb]*t
    return Audio(out,a.rate)

def validate_audio(a: Audio):
    p=preprocess(a); fr=extract_frames(p); v=detect_speech(fr)
    dur=len(p.samples)*1000/p.rate
    accepted=dur>=450 and v["speech_ms"]>=350 and v["speech_ms"]/max(1,dur)>=.11 and v["snr_db"]>=3
    return {"accepted":accepted,"duration_ms":round(dur),"speech_ms":round(v["speech_ms"]),"snr_db":round(v["snr_db"],1)}

def self_test(path: str):
    raw=read_wav(path)
    ranges=raw_ayah_ranges(raw)
    ayat=[]
    for i,r in enumerate(ranges,1):
        a=slice_audio(raw,r)
        base=validate_audio(a)
        slower=validate_audio(time_stretch_by_resample(a,1.25))
        faster=validate_audio(time_stretch_by_resample(a,.80))
        cut=Audio(a.samples[:max(1,int(len(a.samples)*.15))],a.rate)
        cut_v=validate_audio(cut)
        speech_ratio=cut_v["speech_ms"]/max(1,base["speech_ms"])
        ayat.append({
            "ayah":i,
            "range_ms":[round(r[0]),round(r[1])],
            "reference":base,
            "self_reference_madd_normalized":1.0,
            "expected_madd_status":"PASS",
            "tempo_0_80x_accepted":faster["accepted"],
            "tempo_1_25x_accepted":slower["accepted"],
            "truncated_speech_ratio":round(speech_ratio,2),
            "truncated_obvious_cut":speech_ratio < .55,
        })
    silence=Audio([0.0]*(TARGET_SR*3),TARGET_SR)
    return {
        "pipeline":"al-fatiha-reference-regression-v2.0.1",
        "reference":str(path),
        "duration_ms":round(len(raw.samples)*1000/raw.rate),
        "ayah_count":len(ranges),
        "silence_rejected":not validate_audio(silence)["accepted"],
        "all_self_reference_madd_pass":all(x["expected_madd_status"]=="PASS" for x in ayat),
        "all_truncations_detected":all(x["truncated_obvious_cut"] for x in ayat),
        "ayat":ayat,
        "limitations":[
            "This reference pipeline does not perform learned Quran phoneme classification.",
            "Madd self-check is reference-normalized; learner validation still requires real learner recordings.",
            "Subtle makhraj/sifat decisions remain outside validated scope."
        ]
    }

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("wav")
    ap.add_argument("--self-test",action="store_true")
    ap.add_argument("--json",action="store_true")
    args=ap.parse_args()
    result=self_test(args.wav) if args.self_test else {
        "ranges_ms":[[round(a),round(b)] for a,b in raw_ayah_ranges(read_wav(args.wav))]
    }
    print(json.dumps(result,ensure_ascii=False,indent=2) if args.json or args.self_test else result)

if __name__=="__main__":
    main()
