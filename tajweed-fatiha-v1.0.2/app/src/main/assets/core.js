(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.TajweedCore = api;
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  const MODEL_VERSION = "acoustic-dtw-v1.1.0";
  const STATUS = { PASS: "pass", REVIEW: "review", FAIL: "fail", UNDECIDABLE: "undecidable" };
  const DIACRITICS = /[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED]/g;

  const AYAT = [
    {
      number: 1,
      text: "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
      words: ["بِسْمِ", "اللَّهِ", "الرَّحْمَٰنِ", "الرَّحِيمِ"],
      phonemes: [["b","i","s","m"],["a","l","lˤ","aː","h"],["a","r","r","a","ħ","m","aː","n"],["a","r","r","a","ħ","iː","m"]],
      madd: [{word:2,id:"MADD-TABII-1-3",name:"مد طبيعي",target:2},{word:3,id:"MADD-TABII-1-4",name:"مد طبيعي",target:2}]
    },
    {
      number: 2,
      text: "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
      words: ["الْحَمْدُ", "لِلَّهِ", "رَبِّ", "الْعَالَمِينَ"],
      phonemes: [["a","l","ħ","a","m","d"],["l","i","l","lˤ","aː","h"],["r","a","b","b"],["a","l","ʕ","aː","l","a","m","iː","n"]],
      madd: [{word:3,id:"MADD-TABII-2-4A",name:"مد طبيعي",target:2},{word:3,id:"MADD-TABII-2-4B",name:"مد طبيعي",target:2}]
    },
    {
      number: 3,
      text: "الرَّحْمَٰنِ الرَّحِيمِ",
      words: ["الرَّحْمَٰنِ", "الرَّحِيمِ"],
      phonemes: [["a","r","r","a","ħ","m","aː","n"],["a","r","r","a","ħ","iː","m"]],
      madd: [{word:0,id:"MADD-TABII-3-1",name:"مد طبيعي",target:2},{word:1,id:"MADD-TABII-3-2",name:"مد طبيعي",target:2}]
    },
    {
      number: 4,
      text: "مَالِكِ يَوْمِ الدِّينِ",
      words: ["مَالِكِ", "يَوْمِ", "الدِّينِ"],
      phonemes: [["m","aː","l","i","k"],["j","a","w","m"],["a","d","d","iː","n"]],
      madd: [{word:0,id:"MADD-TABII-4-1",name:"مد طبيعي",target:2},{word:2,id:"MADD-TABII-4-3",name:"مد طبيعي",target:2}]
    },
    {
      number: 5,
      text: "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ",
      words: ["إِيَّاكَ", "نَعْبُدُ", "وَإِيَّاكَ", "نَسْتَعِينُ"],
      phonemes: [["ʔ","i","j","j","aː","k"],["n","a","ʕ","b","u","d"],["w","a","ʔ","i","j","j","aː","k"],["n","a","s","t","a","ʕ","iː","n"]],
      madd: [{word:0,id:"MADD-TABII-5-1",name:"مد طبيعي",target:2},{word:2,id:"MADD-TABII-5-3",name:"مد طبيعي",target:2},{word:3,id:"MADD-TABII-5-4",name:"مد طبيعي",target:2}]
    },
    {
      number: 6,
      text: "اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ",
      words: ["اهْدِنَا", "الصِّرَاطَ", "الْمُسْتَقِيمَ"],
      phonemes: [["i","h","d","i","n","aː"],["a","sˤ","sˤ","i","r","aː","tˤ"],["a","l","m","u","s","t","a","q","iː","m"]],
      madd: [{word:0,id:"MADD-TABII-6-1",name:"مد طبيعي",target:2},{word:1,id:"MADD-TABII-6-2",name:"مد طبيعي",target:2},{word:2,id:"MADD-TABII-6-3",name:"مد طبيعي",target:2}]
    },
    {
      number: 7,
      text: "صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ",
      words: ["صِرَاطَ", "الَّذِينَ", "أَنْعَمْتَ", "عَلَيْهِمْ", "غَيْرِ", "الْمَغْضُوبِ", "عَلَيْهِمْ", "وَلَا", "الضَّالِّينَ"],
      phonemes: [["sˤ","i","r","aː","tˤ"],["a","l","l","a","ð","iː","n"],["ʔ","a","n","ʕ","a","m","t"],["ʕ","a","l","a","j","h","i","m"],["ɣ","a","j","r"],["a","l","m","a","ɣ","dˤ","uː","b"],["ʕ","a","l","a","j","h","i","m"],["w","a","l","aː"],["a","dˤ","dˤ","aː","l","l","iː","n"]],
      madd: [{word:0,id:"MADD-TABII-7-1",name:"مد طبيعي",target:2},{word:1,id:"MADD-TABII-7-2",name:"مد طبيعي",target:2},{word:5,id:"MADD-TABII-7-6",name:"مد طبيعي",target:2},{word:7,id:"MADD-TABII-7-8",name:"مد طبيعي",target:2},{word:8,id:"MADD-LAZIM-7-9",name:"مد لازم كلمي مثقل",target:6}]
    }
  ];

  // منهج الأحكام ثابت ومراجع نصيًا. لا تعني هذه القائمة أن المحرك الصوتي
  // يستطيع إثبات كل حكم؛ القياس الصوتي الحالي يدعم المحاذاة والمد بصورة تجريبية.
  const TAJWEED_RULES = {
    1:["لام لفظ الجلالة مرققة","لام شمسية","مد طبيعي"],
    2:["لام قمرية","تفخيم الراء","تشديد الباء","مد طبيعي"],
    3:["لام شمسية","تفخيم الراء","مد طبيعي"],
    4:["مد طبيعي","حرف لين","لام شمسية"],
    5:["تشديد الياء","مد طبيعي","تحقيق الهمزة"],
    6:["لام شمسية","تفخيم الصاد والطاء","قلقلة القاف","مد طبيعي"],
    7:["تفخيم الصاد والطاء","لام شمسية","إظهار حلقي","إظهار شفوي","لام قمرية","مد طبيعي","مد لازم كلمي مثقل ٦ حركات"]
  };

  AYAT.forEach(a=>a.rules=TAJWEED_RULES[a.number].slice());

  function fullSurah() {
    const words = [], phonemes = [], madd = [];
    AYAT.forEach(a => {
      const offset = words.length;
      words.push.apply(words, a.words);
      phonemes.push.apply(phonemes, a.phonemes);
      a.madd.forEach(m => madd.push(Object.assign({}, m, {word:m.word + offset})));
    });
    return { number:0, text:AYAT.map(a=>a.text).join(" ۝ "), words, phonemes, madd, rules:Array.from(new Set(AYAT.flatMap(a=>a.rules))) };
  }

  function clamp(v, min, max) { return Math.max(min, Math.min(max, v)); }
  function median(values) {
    if (!values.length) return 0;
    const a = values.slice().sort((x,y)=>x-y), m = Math.floor(a.length/2);
    return a.length%2 ? a[m] : (a[m-1]+a[m])/2;
  }
  function mean(values) { return values.length ? values.reduce((a,b)=>a+b,0)/values.length : 0; }
  function variance(values) { const m=mean(values); return mean(values.map(v=>(v-m)*(v-m))); }
  function strip(text) { return text.replace(DIACRITICS, "").replace(/[ٱأإآ]/g,"ا"); }
  function wordWeight(word, phonemes) {
    const base = Math.max(2.5, strip(word).length * .82);
    const longCount = phonemes.filter(p=>p.indexOf("ː")>=0).length;
    return base + longCount * 1.15;
  }

  function resample(input, sourceRate, targetRate) {
    if (sourceRate === targetRate) return Float32Array.from(input);
    const ratio = sourceRate / targetRate, n = Math.max(1, Math.floor(input.length / ratio)), out = new Float32Array(n);
    for (let i=0;i<n;i++) {
      const pos=i*ratio, a=Math.floor(pos), b=Math.min(input.length-1,a+1), t=pos-a;
      out[i]=input[a]*(1-t)+input[b]*t;
    }
    return out;
  }

  function preprocess(input, sampleRate) {
    if (!input || !input.length || !sampleRate) throw new Error("تعذر قراءة بيانات الصوت.");
    let pcm=resample(input,sampleRate,16000), avg=mean(Array.from(pcm));
    let peak=0;
    for(let i=0;i<pcm.length;i++){pcm[i]-=avg;peak=Math.max(peak,Math.abs(pcm[i]));}
    if(peak>.0001){const gain=Math.min(3,.92/peak);for(let i=0;i<pcm.length;i++)pcm[i]*=gain;}
    return {pcm,sampleRate:16000,sourceSampleRate:sampleRate,peak};
  }

  function frameFeatures(pcm, sampleRate) {
    const frame=Math.round(sampleRate*.025), hop=Math.round(sampleRate*.010), rows=[];
    for(let start=0;start+frame<=pcm.length;start+=hop){
      let energy=0,z=0,abs=0;
      for(let i=0;i<frame;i++){
        const v=pcm[start+i];energy+=v*v;abs+=Math.abs(v);
        if(i && ((v>=0)!==(pcm[start+i-1]>=0)))z++;
      }
      const rms=Math.sqrt(energy/frame), zcr=z/frame;
      // Three band-like difference features. They are intentionally acoustic, not phoneme labels.
      let low=0,mid=0,high=0;
      for(let i=2;i<frame;i+=3){
        const x=pcm[start+i], d1=x-pcm[start+i-1], d2=d1-(pcm[start+i-1]-pcm[start+i-2]);
        low+=Math.abs(x);mid+=Math.abs(d1);high+=Math.abs(d2);
      }
      const norm=low+mid+high+1e-9;
      rows.push({startMs:start/sampleRate*1000,endMs:(start+frame)/sampleRate*1000,rms,zcr,low:low/norm,mid:mid/norm,high:high/norm,active:false});
    }
    return rows;
  }

  function applyVad(frames) {
    if(!frames.length)return {frames,segments:[],threshold:1,noiseFloor:1,snrDb:0,speechMs:0};
    const rms=frames.map(f=>f.rms), sorted=rms.slice().sort((a,b)=>a-b);
    const noiseFloor=median(sorted.slice(0,Math.max(3,Math.floor(sorted.length*.22))));
    const peak=Math.max.apply(null,rms), threshold=Math.max(.008,noiseFloor*2.8,peak*.07);
    frames.forEach(f=>f.active=f.rms>=threshold && f.zcr<.38);
    const segments=[];let open=-1,last=-1;
    for(let i=0;i<frames.length;i++){
      if(frames[i].active){if(open<0)open=i;last=i;}
      else if(open>=0 && i-last>14){if(last-open>=6)segments.push({startFrame:open,endFrame:last,startMs:frames[open].startMs,endMs:frames[last].endMs});open=-1;last=-1;}
    }
    if(open>=0&&last-open>=6)segments.push({startFrame:open,endFrame:last,startMs:frames[open].startMs,endMs:frames[last].endMs});
    const speechMs=segments.reduce((s,x)=>s+x.endMs-x.startMs,0);
    const speechRms=frames.filter(f=>f.active).map(f=>f.rms);
    const snrDb=20*Math.log10((median(speechRms)||1e-6)/(noiseFloor||1e-6));
    return {frames,segments,threshold,noiseFloor,snrDb:isFinite(snrDb)?snrDb:0,speechMs,peak};
  }

  function featureDistance(a,b){
    const er=Math.abs(Math.log((a.rms+1e-4)/(b.rms+1e-4)))*.18;
    return er+Math.abs(a.zcr-b.zcr)*1.7+Math.abs(a.low-b.low)+Math.abs(a.mid-b.mid)*.8+Math.abs(a.high-b.high)*.55;
  }

  function compactActive(frames,maxFrames){
    let active=frames.filter(f=>f.active);
    const stride=Math.max(1,Math.ceil(active.length/(maxFrames||520)));
    if(stride===1)return active;
    const out=[];
    for(let i=0;i<active.length;i+=stride){
      const g=active.slice(i,i+stride);
      out.push({startMs:g[0].startMs,endMs:g[g.length-1].endMs,rms:mean(g.map(x=>x.rms)),zcr:mean(g.map(x=>x.zcr)),low:mean(g.map(x=>x.low)),mid:mean(g.map(x=>x.mid)),high:mean(g.map(x=>x.high)),active:true});
    }
    return out;
  }

  function dtw(reference, observed) {
    const r=compactActive(reference,420),o=compactActive(observed,420), n=r.length,m=o.length;
    if(!n||!m)return {distance:Infinity,path:[],reference:r,observed:o,insertRatio:1};
    const prev=new Float64Array(m+1),curr=new Float64Array(m+1),trace=new Uint8Array((n+1)*(m+1));
    prev.fill(Infinity);prev[0]=0;
    for(let i=1;i<=n;i++){
      curr.fill(Infinity);
      for(let j=1;j<=m;j++){
        let best=prev[j-1],dir=0;
        if(prev[j]<best){best=prev[j];dir=1;}
        if(curr[j-1]<best){best=curr[j-1];dir=2;}
        curr[j]=featureDistance(r[i-1],o[j-1])+best;trace[i*(m+1)+j]=dir;
      }
      prev.set(curr);
    }
    let i=n,j=m,path=[];while(i>0&&j>0){path.push([i-1,j-1]);const d=trace[i*(m+1)+j];if(d===0){i--;j--;}else if(d===1)i--;else j--;}
    path.reverse();
    const extra=Math.max(0,m-n)/Math.max(1,n);
    return {distance:prev[m]/Math.max(1,path.length),path,reference:r,observed:o,insertRatio:extra};
  }

  function expectedBoundaries(ayah,totalMs) {
    const weights=ayah.words.map((w,i)=>wordWeight(w,ayah.phonemes[i]||[])),sum=weights.reduce((a,b)=>a+b,0),bounds=[0];let acc=0;
    weights.forEach(w=>{acc+=w;bounds.push(totalMs*acc/sum);});
    return {bounds,weights};
  }

  function nearestQuietBoundary(frames,target,min,max) {
    let best=target,score=Infinity;
    for(const f of frames){
      const t=(f.startMs+f.endMs)/2;if(t<min||t>max)continue;
      const s=f.rms*(1+Math.abs(t-target)/Math.max(50,(max-min)));
      if(s<score){score=s;best=t;}
    }
    return best;
  }

  function timeBoundaries(frames,ayah,startMs,endMs) {
    const exp=expectedBoundaries(ayah,endMs-startMs).bounds.map(x=>x+startMs),out=[startMs];
    for(let i=1;i<exp.length-1;i++){
      const left=(exp[i-1]+exp[i])*.5,right=(exp[i]+exp[i+1])*.5;
      out.push(nearestQuietBoundary(frames,exp[i],left,right));
    }
    out.push(endMs);return out;
  }

  function mapReferenceBoundaries(alignment,refBounds) {
    const out=[];
    for(const t of refBounds){
      let best=null,delta=Infinity;
      for(const pair of alignment.path){
        const rf=alignment.reference[pair[0]],d=Math.abs(((rf.startMs+rf.endMs)/2)-t);
        if(d<delta){delta=d;best=alignment.observed[pair[1]];}
      }
      out.push(best?(best.startMs+best.endMs)/2:0);
    }
    for(let i=1;i<out.length;i++)if(out[i]<out[i-1])out[i]=out[i-1];
    return out;
  }

  function stableRun(frames,startMs,endMs,threshold) {
    const f=frames.filter(x=>x.active&&x.startMs>=startMs&&x.endMs<=endMs);
    if(!f.length)return {durationMs:0,confidence:0};
    const zmed=median(f.map(x=>x.zcr)),emed=median(f.map(x=>x.rms));let best=0,run=0;
    for(const x of f){
      const stable=Math.abs(x.zcr-zmed)<.055&&x.rms>Math.max(threshold,emed*.52);
      run=stable?run+(x.endMs-x.startMs):0;best=Math.max(best,run);
    }
    return {durationMs:best,confidence:clamp(best/Math.max(100,(endMs-startMs)*.38),0,1)};
  }

  function estimateHarakah(frames,startMs,endMs) {
    const f=frames.filter(x=>x.active&&x.startMs>=startMs&&x.endMs<=endMs);
    if(f.length<8)return 0;
    const peaks=[];
    for(let i=2;i<f.length-2;i++)if(f[i].rms>f[i-1].rms&&f[i].rms>=f[i+1].rms&&f[i].rms>median([f[i-2].rms,f[i+2].rms])*1.12)peaks.push((f[i].startMs+f[i].endMs)/2);
    const gaps=[];for(let i=1;i<peaks.length;i++){const g=peaks[i]-peaks[i-1];if(g>=65&&g<=420)gaps.push(g);}
    return clamp(median(gaps)||145,80,280);
  }

  function compareWordFeatures(refFrames,obsFrames,rs,re,os,oe){
    const ra=refFrames.filter(x=>x.active&&x.startMs>=rs&&x.endMs<=re),oa=obsFrames.filter(x=>x.active&&x.startMs>=os&&x.endMs<=oe);
    if(!ra.length||!oa.length)return 1;
    const pack=a=>({rms:median(a.map(x=>x.rms)),zcr:median(a.map(x=>x.zcr)),low:mean(a.map(x=>x.low)),mid:mean(a.map(x=>x.mid)),high:mean(a.map(x=>x.high))});
    return featureDistance(pack(ra),pack(oa));
  }

  function analyze(input,sampleRate,ayah,reference) {
    const started=Date.now(),pre=preprocess(input,sampleRate),vad=applyVad(frameFeatures(pre.pcm,pre.sampleRate));
    const durationMs=pre.pcm.length/pre.sampleRate*1000,clipRatio=Array.from(pre.pcm).filter(x=>Math.abs(x)>.985).length/Math.max(1,pre.pcm.length);
    if(durationMs<450)return rejected("التسجيل قصير جدًا. اقرأ الآية كاملة ثم أوقف التسجيل.",vad,pre,started,"TOO_SHORT");
    if(vad.speechMs<350||vad.speechMs/durationMs<.12)return rejected("لم ألتقط تلاوة واضحة. اقترب من الميكروفون وأعد المحاولة.",vad,pre,started,"SILENCE");
    if(vad.snrDb<4)return rejected("الضوضاء أعلى من أن تسمح بحكم موثوق. أعد المحاولة في مكان أهدأ.",vad,pre,started,"LOW_SNR");

    const first=vad.segments[0].startMs,last=vad.segments[vad.segments.length-1].endMs;
    let bounds,refBounds=null,alignment=null,mode="timing-only";
    if(reference&&reference.frames&&reference.vad&&reference.vad.speechMs>300){
      const rf=reference.frames,rv=reference.vad,rfst=rv.segments[0].startMs,rlst=rv.segments[rv.segments.length-1].endMs;
      refBounds=timeBoundaries(rf,ayah,rfst,rlst);alignment=dtw(rf,vad.frames);bounds=mapReferenceBoundaries(alignment,refBounds);bounds[0]=first;bounds[bounds.length-1]=last;mode="reference-dtw";
    }else bounds=timeBoundaries(vad.frames,ayah,first,last);

    const quality=clamp((vad.snrDb-4)/16,0,1)*clamp(vad.speechMs/Math.max(800,ayah.words.length*330),0,1)*(1-clamp(clipRatio*8,0,.8));
    const harakah=estimateHarakah(vad.frames,first,last),maddByWord={};
    ayah.madd.forEach(event=>{
      const ws=bounds[event.word],we=bounds[event.word+1],stable=stableRun(vad.frames,ws,we,vad.threshold),ratio=harakah?stable.durationMs/harakah:0;
      let status=STATUS.UNDECIDABLE,explanation="لم أتمكن من قياس المد بثقة كافية.";
      const measureConfidence=quality*stable.confidence*(harakah?1:0);
      if(measureConfidence>=.42){
        const min=event.target===6?4.1:1.25,max=event.target===6?7.8:3.45;
        if(ratio<min){status=ratio<min*.68?STATUS.FAIL:STATUS.REVIEW;explanation="المد أقصر من النطاق المتوقع نسبةً إلى سرعة تلاوتك.";}
        else if(ratio>max){status=ratio>max*1.35?STATUS.FAIL:STATUS.REVIEW;explanation="المد أطول من النطاق المتوقع نسبةً إلى سرعة تلاوتك.";}
        else {status=STATUS.PASS;explanation="مدة المد واقعة داخل النطاق النسبي المقبول في هذا القياس.";}
      }
      const item={id:event.id,name:event.name,targetHarakat:event.target,observedRatio:+ratio.toFixed(2),durationMs:Math.round(stable.durationMs),harakahMs:Math.round(harakah),status,confidence:+measureConfidence.toFixed(2),explanation};
      (maddByWord[event.word]||(maddByWord[event.word]=[])).push(item);
    });

    const words=ayah.words.map((word,i)=>{
      const startMs=Math.round(bounds[i]),endMs=Math.round(bounds[i+1]),duration=endMs-startMs;
      let status=STATUS.UNDECIDABLE,confidence=quality*.55,reason="تمت محاذاة هذا الموضع زمنيًا، لكن النطق الفونيمي غير مدعوم دون نموذج قرآني معتمد.";
      let acousticDistance=null,durationRatio=null;
      if(reference&&alignment&&refBounds){
        const rd=refBounds[i+1]-refBounds[i];durationRatio=rd>0?duration/rd:0;
        acousticDistance=compareWordFeatures(reference.frames,vad.frames,refBounds[i],refBounds[i+1],bounds[i],bounds[i+1]);
        const alignConfidence=clamp(1-alignment.distance/1.05,0,1)*quality;confidence=alignConfidence;
        if(durationRatio<.46&&alignConfidence>.55){status=STATUS.FAIL;reason="يوجد نقص زمني واضح عند مقارنة هذا الموضع بالمرجع المستورد؛ قد تكون الكلمة ناقصة أو مبتورة.";}
        else if(acousticDistance>.72&&alignConfidence>.62){status=STATUS.REVIEW;reason="البصمة الصوتية لهذا الموضع بعيدة عن المرجع المستورد. استمع ثم أعد المحاولة.";}
        else if(alignConfidence>.72){status=STATUS.PASS;reason="المحاذاة والبصمة الصوتية قريبتان من المرجع المستورد، ضمن حدود النموذج التجريبي.";}
      }
      const madd=maddByWord[i]||[];
      if(madd.some(x=>x.status===STATUS.FAIL)){status=STATUS.FAIL;confidence=Math.max(confidence,...madd.map(x=>x.confidence));reason=madd.find(x=>x.status===STATUS.FAIL).explanation;}
      else if(madd.some(x=>x.status===STATUS.REVIEW)&&status!==STATUS.FAIL){status=STATUS.REVIEW;confidence=Math.max(confidence,...madd.map(x=>x.confidence));reason=madd.find(x=>x.status===STATUS.REVIEW).explanation;}
      else if(madd.some(x=>x.status===STATUS.PASS)&&status===STATUS.UNDECIDABLE){status=STATUS.PASS;confidence=Math.max(confidence,...madd.map(x=>x.confidence));reason=madd.find(x=>x.status===STATUS.PASS).explanation+" لم يُحكم على بقية أصوات الكلمة.";}
      return {index:i,word,startMs,endMs,durationMs:duration,status,confidence:+clamp(confidence,0,1).toFixed(2),reason,expectedPhonemes:ayah.phonemes[i],observedPhonemes:null,acousticDistance:acousticDistance===null?null:+acousticDistance.toFixed(3),durationRatio:durationRatio===null?null:+durationRatio.toFixed(2),madd};
    });

    const counts={pass:0,review:0,fail:0,undecidable:0};words.forEach(w=>counts[w.status]++);
    const supported=words.filter(w=>w.madd.length||(mode==="reference-dtw"&&w.status!==STATUS.UNDECIDABLE));
    let overall=STATUS.UNDECIDABLE,title="القياس غير حاسم";
    if(counts.fail){overall=STATUS.FAIL;title="رُصدت مشكلة واضحة في موضع واحد أو أكثر";}
    else if(counts.review){overall=STATUS.REVIEW;title="توجد مواضع يُفضّل مراجعتها";}
    else if(supported.length&&counts.pass){
      const enoughCoverage=counts.pass>=Math.ceil(words.length*.6);
      overall=quality>.65&&enoughCoverage?STATUS.PASS:STATUS.REVIEW;
      title=overall===STATUS.PASS?"القياسات المدعومة جيدة":"القياس جيد مبدئيًا لكن التغطية أو الثقة محدودة";
    }
    const issues=[];
    words.filter(w=>w.status===STATUS.FAIL||w.status===STATUS.REVIEW).forEach(w=>issues.push({word:w.word,status:w.status,title:w.status===STATUS.FAIL?"مشكلة واضحة":"يحتاج إلى مراجعة",text:w.reason}));
    if(mode!=="reference-dtw")issues.push({status:STATUS.UNDECIDABLE,title:"التعرف على الكلمات محدود",text:"لم يُستورد مرجع صوتي مرخّص؛ لذلك لا يدّعي التطبيق اكتشاف استبدال كلمة أو صحة المخارج."});
    if(!issues.length)issues.push({status:STATUS.PASS,title:"لا توجد مشكلة مدعومة واضحة",text:"هذه النتيجة تخص المحاذاة والمد فقط، وليست تصحيحًا شاملًا للتجويد."});
    return {accepted:true,overall,title,mode,confidence:+quality.toFixed(2),durationMs:Math.round(durationMs),speechMs:Math.round(vad.speechMs),snrDb:+vad.snrDb.toFixed(1),clipRatio:+clipRatio.toFixed(4),harakahMs:Math.round(harakah),sampleRate:pre.sampleRate,sourceSampleRate:pre.sourceSampleRate,modelVersion:MODEL_VERSION,processingMs:Date.now()-started,counts,words,issues,alignment:alignment?{distance:+alignment.distance.toFixed(3),pathPoints:alignment.path.length,insertRatio:+alignment.insertRatio.toFixed(3)}:null,triggeredRuleIds:ayah.madd.map(x=>x.id),limitations:["لا يوجد تعرف فونيمي قرآني متعلم في هذه النسخة.","المحاذاة المرجعية تعتمد على DTW ومرجع يستورده المستخدم.","تقييم المد تقديري ومبني على الاستقرار الصوتي والسرعة المحلية."]};
  }

  function rejected(message,vad,pre,started,code){
    return {accepted:false,overall:STATUS.UNDECIDABLE,title:"تعذر تحليل المحاولة",errorCode:code,message,confidence:0,durationMs:Math.round(pre.pcm.length/pre.sampleRate*1000),speechMs:Math.round(vad.speechMs),snrDb:+vad.snrDb.toFixed(1),sampleRate:pre.sampleRate,sourceSampleRate:pre.sourceSampleRate,modelVersion:MODEL_VERSION,processingMs:Date.now()-started,words:[],issues:[{status:STATUS.UNDECIDABLE,title:"أعد المحاولة",text:message}],triggeredRuleIds:[]};
  }

  function makeReference(input,sampleRate){
    const pre=preprocess(input,sampleRate),frames=frameFeatures(pre.pcm,pre.sampleRate),vad=applyVad(frames);
    if(vad.speechMs<350)throw new Error("المرجع الصوتي لا يحتوي تلاوة واضحة.");
    return {frames,vad,sampleRate:pre.sampleRate,modelVersion:MODEL_VERSION};
  }

  return {AYAT,TAJWEED_RULES,fullSurah,STATUS,MODEL_VERSION,preprocess,frameFeatures,applyVad,dtw,analyze,makeReference,strip,median,clamp};
});
