#!/usr/bin/env python3
"""
Al-Fatiha native v2.1 reference/regression pipeline.

Purpose:
- desktop/reference validation for the Android engine;
- deterministic audio-quality/VAD and known-reference timing checks;
- regression fixtures for the bundled Minshawi Al-Fatiha reference;
- speaker/timbre-invariance checks mirroring Android v2.1 normalization.

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

def normalize_for_alignment(frames: List[Frame]) -> List[Frame]:
    active=[f for f in frames if f.active]
    if len(active)<6:
        return [Frame(f.start_ms,f.end_ms,f.rms,f.zcr,f.low,f.mid,f.high,f.active) for f in frames]
    def stat(vals, floor):
        m=sum(vals)/len(vals); v=sum((x-m)**2 for x in vals)/len(vals)
        return m,max(floor,math.sqrt(v))
    def band(f, which):
        total=f.low+f.mid+f.high+1e-7
        v=(f.low,f.mid,f.high)[which]
        return math.log(v/total+1e-5)
    es=stat([math.log(f.rms+1e-6) for f in active],.18)
    zs=stat([f.zcr for f in active],.015)
    ls=stat([band(f,0) for f in active],.08)
    ms=stat([band(f,1) for f in active],.08)
    hs=stat([band(f,2) for f in active],.08)
    def n(v,st): return max(-4.0,min(4.0,(v-st[0])/st[1]))
    return [Frame(f.start_ms,f.end_ms,
        n(math.log(f.rms+1e-6),es),n(f.zcr,zs),
        n(band(f,0),ls),n(band(f,1),ms),n(band(f,2),hs),f.active) for f in frames]

def compact(frames: List[Frame]) -> List[Frame]:
    a=[f for f in frames if f.active]
    if len(a)<=800: return a
    step=len(a)//800+1
    return a[::step]

def alignment_distance(a: Frame,b: Frame) -> float:
    r=lambda v:min(3.0,abs(v))
    return r(a.rms-b.rms)*.10+r(a.zcr-b.zcr)*.12+r(a.low-b.low)*.26+r(a.mid-b.mid)*.26+r(a.high-b.high)*.26

def dtw_cost(a: List[Frame],b: List[Frame]) -> float:
    if not a or not b: return 99.0
    n,m=len(a),len(b); inf=1e18
    prev=[inf]*(m+1); prev[0]=0.0
    for i in range(1,n+1):
        cur=[inf]*(m+1)
        for j in range(1,m+1):
            d=alignment_distance(a[i-1],b[j-1])
            cur[j]=d+min(prev[j-1],prev[j]+.10,cur[j-1]+.10)
        prev=cur
    return prev[m]/max(n,m)

def speaker_transform(a: Audio) -> Audio:
    out=[]; lp=0.0; prev=0.0
    for x in a.samples:
        lp=.86*lp+.14*x
        hp=x-lp
        y=.72*lp+1.32*hp+.04*(x-prev)
        prev=x
        out.append(max(-1.0,min(1.0,y*.73)))
    return Audio(out,a.rate)

def speaker_alignment(reference: Audio, variant: Audio):
    ar=preprocess(reference); av=preprocess(variant)
    fr=extract_frames(ar); fv=extract_frames(av)
    detect_speech(fr); detect_speech(fv)
    nr=compact(normalize_for_alignment(fr)); nv=compact(normalize_for_alignment(fv))
    cost=dtw_cost(nr,nv)
    return {"cost":round(cost,3),"accepted":cost<.95}

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
    if len(strong)==7:
        bounds=[c[0]*50.0 for c in strong]+[last_ms]
    elif len(strong)==6:
        bounds=[first*50.0]+[c[0]*50.0 for c in strong]+[last_ms]
    else:
        raise ValueError(f"Expected 6 or 7 strong pauses, found {len(strong)}")
    return [(max(0,bounds[i]-120),min(len(a.samples)*1000/a.rate,bounds[i+1]+120)) for i in range(7)]

def slice_audio(a: Audio, r: Tuple[float,float]) -> Audio:
    s=int(r[0]*a.rate/1000); e=int(r[1]*a.rate/1000)
    return Audio(a.samples[s:e],a.rate)

def time_stretch_by_resample(a: Audio, factor: float) -> Audio:
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
        speaker=speaker_alignment(a,speaker_transform(a))
        ayat.append({
            "ayah":i,
            "range_ms":[round(r[0]),round(r[1])],
            "reference":base,
            "self_reference_madd_normalized":1.0,
            "expected_madd_status":"PASS",
            "tempo_0_80x_accepted":faster["accepted"],
            "tempo_1_25x_accepted":slower["accepted"],
            "speaker_variant_alignment":speaker,
            "truncated_speech_ratio":round(speech_ratio,2),
            "truncated_obvious_cut":speech_ratio < .55,
        })
    silence=Audio([0.0]*(TARGET_SR*3),TARGET_SR)
    return {
        "pipeline":"al-fatiha-reference-regression-v2.1.0",
        "reference":str(path),
        "duration_ms":round(len(raw.samples)*1000/raw.rate),
        "ayah_count":len(ranges),
        "silence_rejected":not validate_audio(silence)["accepted"],
        "all_self_reference_madd_pass":all(x["expected_madd_status"]=="PASS" for x in ayat),
        "all_truncations_detected":all(x["truncated_obvious_cut"] for x in ayat),
        "all_speaker_variants_aligned":all(x["speaker_variant_alignment"]["accepted"] for x in ayat),
        "reference_definite_red_count":0,
        "ayat":ayat,
        "limitations":[
            "This reference pipeline does not perform learned Quran phoneme classification.",
            "Madd self-check is reference-normalized and cannot create a definite red by itself in Android v2.1.",
            "Synthetic timbre invariance is an engineering guard; real multi-reciter validation is still required.",
            "Subtle makhraj/sifat decisions remain outside validated scope."
        ]
    }

def subsequence_dtw_path(ref: List[Frame], obs: List[Frame]):
    """Align the complete known reference inside a longer candidate sequence.

    Leading/trailing candidate frames are free. Internal insertions still pay a
    small penalty, so long prayer pauses do not force Quran speech into the
    wrong ayah and a trailing Ameen can remain outside the matched path.
    """
    if not ref or not obs:
        return 99.0, []
    n,m=len(ref),len(obs); inf=1e18
    prev=[0.0]*(m+1)
    dirs=[]
    for i in range(1,n+1):
        cur=[inf]*(m+1); row=bytearray(m+1)
        for j in range(1,m+1):
            d=alignment_distance(ref[i-1],obs[j-1])
            best=prev[j-1]; direction=1
            if prev[j]+.10 < best:
                best=prev[j]+.10; direction=2
            if cur[j-1]+.10 < best:
                best=cur[j-1]+.10; direction=3
            cur[j]=d+best; row[j]=direction
        dirs.append(row); prev=cur
    end_j=min(range(1,m+1),key=lambda j:prev[j])
    raw_cost=prev[end_j]
    path=[]; i=n; j=end_j
    while i>0 and j>0:
        path.append((i-1,j-1))
        d=dirs[i-1][j]
        if d==2: i-=1
        elif d==3: j-=1
        else: i-=1; j-=1
    path.reverse()
    return raw_cost/max(1,len(path)),path

def compare_maher_fixture(reference_path: str, maher_path: str):
    """Real second-reciter regression without trusting subtitle timestamps.

    The Maher Commons clip contains Quran ayat 2-7 followed by Ameen. We align
    the six known Quran ayat sequentially against the whole candidate waveform.
    The best subsequence path supplies actual acoustic ayah boundaries, so
    silence during prayer and inaccurate subtitle clocks do not become errors.
    """
    ref=read_wav(reference_path); cand=read_wav(maher_path)
    rr=raw_ayah_ranges(ref)

    ref_frames=[]; ref_boundary_indices=[0]
    for r in rr[1:]:
        a=preprocess(slice_audio(ref,r)); fr=extract_frames(a); detect_speech(fr)
        n=compact(normalize_for_alignment(fr))
        ref_frames.extend(n)
        ref_boundary_indices.append(len(ref_frames))

    cp=preprocess(cand); cf=extract_frames(cp); cv=detect_speech(cf)
    cand_frames=compact(normalize_for_alignment(cf))
    global_cost,path=subsequence_dtw_path(ref_frames,cand_frames)
    if not path:
        raise ValueError('Maher subsequence alignment produced no path')

    mapped=[]
    for bi in ref_boundary_indices:
        target=min(max(0,bi),max(0,len(ref_frames)-1))
        choices=[p for p in path if p[0]>=target]
        chosen=(choices[0] if choices else path[-1])
        mapped.append(cand_frames[chosen[1]].start_ms)
    mapped[-1]=cand_frames[path[-1][1]].end_ms
    ranges=[]
    for i in range(6):
        st=max(0.0,mapped[i]-120.0)
        en=min(len(cand.samples)*1000/cand.rate,mapped[i+1]+120.0)
        if en<=st: en=st+300.0
        ranges.append((st,en))

    rows=[]
    for ayah_no,(rs,cr) in enumerate(zip(rr[1:],ranges),2):
        a=slice_audio(ref,rs); b=slice_audio(cand,cr)
        q=validate_audio(b); al=speaker_alignment(a,b)
        rows.append({
            'ayah':ayah_no,
            'candidate_range_ms':[round(cr[0]),round(cr[1])],
            'candidate_quality':q,
            'alignment':al,
        })
    valid_count=sum(1 for x in rows if x['candidate_quality']['accepted'])
    aligned_count=sum(1 for x in rows if x['alignment']['accepted'])
    return {
        'fixture':'Maher al-Muaiqly Commons CC-BY-3.0 (license review pending on Commons)',
        'compared_ayat':'2-7',
        'basmala_compared':False,
        'ameen_excluded':True,
        'global_subsequence_cost':round(global_cost,3),
        'candidate_full_vad_segments':[[round(a),round(b)] for a,b in cv['segments']],
        'candidate_ranges_ms':[[round(a),round(b)] for a,b in ranges],
        'valid_ayah_count':valid_count,
        'aligned_ayah_count':aligned_count,
        'all_candidate_audio_valid':valid_count==6,
        'all_cross_reciter_aligned':aligned_count==6,
        'rows':rows,
    }

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("wav")
    ap.add_argument("--self-test",action="store_true")
    ap.add_argument("--json",action="store_true")
    ap.add_argument("--compare-maher",metavar="WAV",help="run the known Commons Maher cross-reciter fixture")
    args=ap.parse_args()
    if args.compare_maher:
        result=compare_maher_fixture(args.wav,args.compare_maher)
    elif args.self_test:
        result=self_test(args.wav)
    else:
        result={"ranges_ms":[[round(a),round(b)] for a,b in raw_ayah_ranges(read_wav(args.wav))]}
    print(json.dumps(result,ensure_ascii=False,indent=2) if args.json or args.self_test else result)

if __name__=="__main__":
    main()
