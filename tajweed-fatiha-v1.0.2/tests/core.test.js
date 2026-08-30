const assert = require("assert");
const C = require("../app/src/main/assets/core.js");

function signal(seconds, rate=16000, options={}) {
  const out=new Float32Array(seconds*rate),freq=options.freq||185,cycle=options.cycle||.22;
  for(let i=0;i<out.length;i++){
    const t=i/rate, phase=t%cycle;
    const voiced=phase<cycle*.72;
    const amp=voiced?(options.amp||.23):(.002*Math.sin(2*Math.PI*47*t));
    out[i]=amp*Math.sin(2*Math.PI*freq*t)+.003*Math.sin(2*Math.PI*371*t);
  }
  return out;
}

function finiteTree(value,path="root") {
  if(typeof value==="number") assert(Number.isFinite(value),`${path} is not finite`);
  else if(Array.isArray(value)) value.forEach((x,i)=>finiteTree(x,`${path}[${i}]`));
  else if(value&&typeof value==="object") Object.keys(value).forEach(k=>finiteTree(value[k],`${path}.${k}`));
}

const tests=[];
function test(name,fn){tests.push({name,fn});}

test("Al-Fatiha canonical pack contains seven ayat",()=>{
  assert.equal(C.AYAT.length,7);
  assert.equal(C.fullSurah().words.length,29);
  assert(C.AYAT.every(a=>a.words.length===a.phonemes.length));
});

test("silence never produces a score",()=>{
  const r=C.analyze(new Float32Array(16000*3),16000,C.AYAT[0],null);
  assert.equal(r.accepted,false);
  assert.equal(r.errorCode,"SILENCE");
  assert.equal(r.confidence,0);
});

test("too-short audio is rejected with a readable error",()=>{
  const r=C.analyze(signal(.25),16000,C.AYAT[0],null);
  assert.equal(r.accepted,false);
  assert.equal(r.errorCode,"TOO_SHORT");
  assert(r.message.length>10);
});

test("valid voiced input runs through local analysis",()=>{
  const r=C.analyze(signal(4),16000,C.AYAT[0],null);
  assert.equal(r.accepted,true);
  assert.equal(r.words.length,4);
  assert(r.triggeredRuleIds.length>=1);
  finiteTree(r);
});

test("identical acoustic sequences have a low DTW distance",()=>{
  const pre=C.preprocess(signal(3),16000),frames=C.applyVad(C.frameFeatures(pre.pcm,pre.sampleRate)).frames;
  const a=C.dtw(frames,frames);
  assert(a.path.length>20);
  assert(a.distance<.001);
});

test("reference mode exposes alignment diagnostics",()=>{
  const refPcm=signal(4),reference=C.makeReference(refPcm,16000);
  const r=C.analyze(signal(4,16000,{freq:187}),16000,C.AYAT[1],reference);
  assert.equal(r.accepted,true);
  assert.equal(r.mode,"reference-dtw");
  assert(r.alignment.pathPoints>0);
  finiteTree(r);
});

test("a controlled temporal deletion is flagged against a reference",()=>{
  const reference=C.makeReference(signal(5),16000);
  const r=C.analyze(signal(3.1),16000,C.AYAT[0],reference);
  assert.equal(r.accepted,true);
  assert(r.words.some(w=>w.status==="fail"&&w.durationRatio<.46));
});

test("all configured madd rules resolve to valid words",()=>{
  for(const a of C.AYAT) for(const m of a.madd){
    assert(m.word>=0&&m.word<a.words.length,`${m.id} word index`);
    assert([2,6].includes(m.target),`${m.id} target`);
  }
});

test("every ayah has a complete tajweed learning plan",()=>{
  assert(C.AYAT.every(a=>Array.isArray(a.rules)&&a.rules.length>=3));
  assert(C.AYAT[6].rules.some(x=>x.includes("مد لازم")));
  assert(C.fullSurah().rules.length>=8);
});

(async()=>{
  let failed=0;
  for(const t of tests){
    try{await t.fn();console.log(`PASS ${t.name}`);}catch(e){failed++;console.error(`FAIL ${t.name}\n${e.stack}`);}
  }
  console.log(`\n${tests.length-failed}/${tests.length} tests passed`);
  process.exitCode=failed?1:0;
})();
