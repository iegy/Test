(function(){
  "use strict";
  const C=window.TajweedCore;
  const $=id=>document.getElementById(id);
  const els={tabs:$("ayahTabs"),text:$("ayahText"),hint:$("ayahHint"),rules:$("tajweedRules"),record:$("recordButton"),recordStatus:$("recordStatus"),meter:$("meter").firstElementChild,analysis:$("analysisCard"),progress:$("progress").firstElementChild,resultTitle:$("resultTitle"),confidence:$("confidenceBadge"),resultWords:$("resultWords"),issues:$("issues"),history:$("history"),referenceInput:$("referenceInput"),referenceState:$("referenceState"),playReference:$("playReference"),playRecording:$("playRecording"),testInput:$("testInput"),detailDialog:$("detailDialog"),detailWord:$("detailWord"),detailContent:$("detailContent"),debug:$("debugPanel"),debugOutput:$("debugOutput")};
  let selected=1,recording=false,stream=null,audioContext=null,processor=null,chunks=[],startedAt=0,lastResult=null,referenceAudio=null,referenceSource="",builtInBuffer=null,builtInSegments=null,lastRecording=null,playbackContext=null;
  const labels={pass:"تم القياس",review:"راجع",fail:"مشكلة",undecidable:"غير محسوم"};

  function ayah(){return selected===0?C.fullSurah():C.AYAT[selected-1];}
  function initTabs(){
    const full=document.createElement("button");full.className="ayah-tab full";full.textContent="السورة كاملة";full.dataset.n="0";els.tabs.appendChild(full);
    C.AYAT.forEach(a=>{const b=document.createElement("button");b.className="ayah-tab";b.textContent=a.number;b.dataset.n=a.number;b.setAttribute("role","tab");els.tabs.appendChild(b);});
    els.tabs.addEventListener("click",e=>{const b=e.target.closest("button");if(!b||recording)return;selected=+b.dataset.n;renderSelection();});
  }
  async function renderSelection(){
    Array.from(els.tabs.children).forEach(b=>b.classList.toggle("active",+b.dataset.n===selected));
    const a=ayah();
    els.text.innerHTML=a.text.split(" ۝ ").map((x,i)=>x+(selected===0?` <span class="ayah-end">${i+1}</span>`:` <span class="ayah-end">${a.number}</span>`)).join(" ");
    els.hint.textContent=selected===0?"سجّل السورة كاملة دون استعجال؛ الحد الأقصى دقيقتان.":`الآية ${a.number} · ${a.words.length} كلمات · ${a.madd.length} مواضع مد قابلة للقياس التجريبي`;
    els.rules.innerHTML=(a.rules||[]).map(x=>`<span>${escapeHtml(x)}</span>`).join("");
    els.analysis.classList.add("hidden");lastResult=null;await refreshReference();renderHistory();
  }

  function openDb(){return new Promise((resolve,reject)=>{const r=indexedDB.open("tajweed-local",1);r.onupgradeneeded=()=>r.result.createObjectStore("references");r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});}
  async function dbGet(key){const db=await openDb();return new Promise((resolve,reject)=>{const r=db.transaction("references","readonly").objectStore("references").get(String(key));r.onsuccess=()=>resolve(r.result||null);r.onerror=()=>reject(r.error);});}
  async function dbPut(key,value){const db=await openDb();return new Promise((resolve,reject)=>{const tx=db.transaction("references","readwrite");tx.objectStore("references").put(value,String(key));tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);});}
  async function ensureBuiltIn(){
    if(builtInBuffer)return builtInBuffer;
    const res=await fetch("fatiha-reference-cc0.ogg");if(!res.ok)throw new Error("تعذر تحميل المرجع المدمج");
    const ctx=new (window.AudioContext||window.webkitAudioContext)();
    try{builtInBuffer=await ctx.decodeAudioData(await res.arrayBuffer());builtInSegments=findAyahSegments(builtInBuffer);return builtInBuffer;}finally{ctx.close();}
  }
  function findAyahSegments(buffer){
    const pcm=buffer.getChannelData(0),rate=buffer.sampleRate,frame=Math.max(1,Math.floor(rate*.05)),energy=[];
    for(let s=0;s<pcm.length;s+=frame){let sum=0;const e=Math.min(pcm.length,s+frame);for(let i=s;i<e;i++)sum+=pcm[i]*pcm[i];energy.push(Math.sqrt(sum/Math.max(1,e-s)));}
    const sorted=energy.slice().sort((a,b)=>a-b),floor=sorted[Math.floor(sorted.length*.15)]||0,peak=Math.max.apply(null,energy),active=energy.map(x=>x>Math.max(floor*2.8,peak*.035));
    let first=active.indexOf(true),last=active.lastIndexOf(true);if(first<0){first=0;last=energy.length-1;}
    const weights=C.AYAT.map(a=>a.words.length+a.madd.length*.32),total=weights.reduce((a,b)=>a+b,0),bounds=[first*frame/rate];let acc=0;
    for(let k=0;k<6;k++){acc+=weights[k];const target=first+(last-first)*acc/total,span=Math.max(8,Math.floor((last-first)*.075));let best=Math.round(target),bestScore=Infinity;for(let i=Math.max(first+1,Math.floor(target-span));i<=Math.min(last-1,Math.ceil(target+span));i++){let local=0;for(let j=Math.max(first,i-3);j<=Math.min(last,i+3);j++)local+=energy[j];const score=local*(1+Math.abs(i-target)/span*.18);if(score<bestScore){bestScore=score;best=i;}}bounds.push(best*frame/rate);}
    bounds.push(Math.min(buffer.duration,(last+1)*frame/rate));return C.AYAT.map((_,i)=>({start:bounds[i],end:bounds[i+1]}));
  }
  function builtInSlice(){
    if(!builtInBuffer)return null;const seg=selected===0?{start:0,end:builtInBuffer.duration}:builtInSegments[selected-1],start=Math.max(0,seg.start-.12),end=Math.min(builtInBuffer.duration,seg.end+.12),src=builtInBuffer.getChannelData(0);return {pcm:Float32Array.from(src.subarray(Math.floor(start*builtInBuffer.sampleRate),Math.floor(end*builtInBuffer.sampleRate))),rate:builtInBuffer.sampleRate,start,end};
  }
  async function refreshReference(){
    try{
      referenceAudio=await dbGet(selected);
      if(referenceAudio){referenceSource="custom";els.referenceState.textContent="مرجع محلي اختياري — لا يُرفع إلى أي جهة";}
      else{await ensureBuiltIn();referenceSource="built-in";els.referenceState.textContent=selected===0?"تلاوة الفاتحة كاملة مدمجة · CC0":"مقطع الآية من تلاوة الفاتحة المدمجة · CC0";}
      els.playReference.disabled=false;
    }catch(_){referenceAudio=null;referenceSource="";els.playReference.disabled=true;els.referenceState.textContent="تعذر تجهيز المرجع الصوتي";}
  }
  els.referenceInput.addEventListener("change",async e=>{const file=e.target.files&&e.target.files[0];if(!file)return;if(file.size>30*1024*1024){toast("حجم الملف أكبر من 30 ميجابايت.");return;}try{els.referenceState.textContent="جارٍ فحص المرجع…";const decoded=await decodeBlob(file);C.makeReference(decoded.pcm,decoded.rate);await dbPut(selected,file);await refreshReference();toast("تم حفظ المرجع على هذا الجهاز.");}catch(err){els.referenceState.textContent="المرجع غير صالح";toast(err.message||"تعذر قراءة الملف.");}finally{e.target.value="";}});
  els.playReference.addEventListener("click",async()=>{try{if(referenceSource==="custom"&&referenceAudio){const url=URL.createObjectURL(referenceAudio),a=new Audio(url);a.onended=()=>URL.revokeObjectURL(url);a.onerror=()=>URL.revokeObjectURL(url);await a.play();return;}await ensureBuiltIn();const seg=selected===0?{start:0,end:builtInBuffer.duration}:builtInSegments[selected-1];playBuffer(builtInBuffer,seg.start,seg.end);}catch(_){toast("تعذر تشغيل المرجع.");}});

  function playBuffer(buffer,start=0,end=buffer.duration){if(playbackContext)playbackContext.close().catch(()=>{});playbackContext=new (window.AudioContext||window.webkitAudioContext)();const src=playbackContext.createBufferSource();src.buffer=buffer;src.connect(playbackContext.destination);src.start(0,start,Math.max(.05,end-start));src.onended=()=>{playbackContext.close().catch(()=>{});playbackContext=null;};}
  function playPcm(pcm,rate){if(playbackContext)playbackContext.close().catch(()=>{});playbackContext=new (window.AudioContext||window.webkitAudioContext)();const b=playbackContext.createBuffer(1,pcm.length,rate);b.copyToChannel(pcm,0);const src=playbackContext.createBufferSource();src.buffer=b;src.connect(playbackContext.destination);src.start();src.onended=()=>{playbackContext.close().catch(()=>{});playbackContext=null;};}

  async function decodeBlob(blob){
    const buf=await blob.arrayBuffer(),ctx=new (window.AudioContext||window.webkitAudioContext)();
    try{const audio=await ctx.decodeAudioData(buf.slice(0)),channel=audio.getChannelData(0);return {pcm:Float32Array.from(channel),rate:audio.sampleRate};}finally{ctx.close();}
  }

  els.testInput.addEventListener("change",async e=>{const file=e.target.files&&e.target.files[0];if(!file)return;try{if(file.size>30*1024*1024)throw new Error("حجم الملف أكبر من 30 ميجابايت.");const d=await decodeBlob(file);lastRecording={pcm:d.pcm,rate:d.rate};els.playRecording.disabled=false;els.recordStatus.textContent="تم تحميل ملف الاختبار. جارٍ التحليل المحلي…";await runAnalysis(d.pcm,d.rate);}catch(err){toast(err.message||"تعذر قراءة ملف الاختبار.");}finally{e.target.value="";}});
  els.playRecording.addEventListener("click",()=>{if(lastRecording)playPcm(lastRecording.pcm,lastRecording.rate);});

  els.record.addEventListener("click",()=>recording?stopRecording():startRecording());
  async function startRecording(){
    if(!navigator.mediaDevices||!navigator.mediaDevices.getUserMedia){toast("التسجيل غير مدعوم على هذا الجهاز.");return;}
    try{
      stream=await navigator.mediaDevices.getUserMedia({audio:{channelCount:1,echoCancellation:false,noiseSuppression:false,autoGainControl:false}});
      audioContext=new (window.AudioContext||window.webkitAudioContext)();await audioContext.resume();
      const source=audioContext.createMediaStreamSource(stream);processor=audioContext.createScriptProcessor(4096,1,1);chunks=[];
      const silent=audioContext.createGain();silent.gain.value=0;source.connect(processor);processor.connect(silent);silent.connect(audioContext.destination);
      processor.onaudioprocess=e=>{const x=Float32Array.from(e.inputBuffer.getChannelData(0));chunks.push(x);let peak=0;for(let i=0;i<x.length;i++)peak=Math.max(peak,Math.abs(x[i]));els.meter.style.width=Math.round(Math.min(1,peak*2.3)*100)+"%";const sec=Math.floor((Date.now()-startedAt)/1000);els.recordStatus.textContent=`جارٍ التسجيل… ${sec} ثانية · اضغط عند الانتهاء`;if(sec>=(selected===0?120:35))stopRecording();};
      startedAt=Date.now();recording=true;els.record.classList.add("recording");els.record.querySelector("strong").textContent="أوقف التسجيل";els.record.querySelector("small").textContent="اضغط عند الانتهاء";els.analysis.classList.add("hidden");
    }catch(err){toast(err&&err.name==="NotAllowedError"?"يلزم السماح باستخدام الميكروفون لبدء التدريب.":"تعذر تشغيل الميكروفون.");cleanupAudio();}
  }
  function stopRecording(){
    if(!recording)return;const rate=audioContext.sampleRate;recording=false;els.record.classList.remove("recording");els.record.querySelector("strong").textContent="اقرأ الآن";els.record.querySelector("small").textContent="اضغط لبدء التسجيل";els.meter.style.width="0";
    const length=chunks.reduce((s,x)=>s+x.length,0),pcm=new Float32Array(length);let pos=0;chunks.forEach(x=>{pcm.set(x,pos);pos+=x.length;});lastRecording={pcm:Float32Array.from(pcm),rate};els.playRecording.disabled=false;cleanupAudio();els.recordStatus.textContent="اكتمل التسجيل. جارٍ التحليل المحلي…";runAnalysis(pcm,rate);
  }
  function cleanupAudio(){if(processor){processor.disconnect();processor.onaudioprocess=null;}if(stream)stream.getTracks().forEach(t=>t.stop());if(audioContext)audioContext.close().catch(()=>{});processor=null;stream=null;audioContext=null;}

  async function runAnalysis(pcm,rate){
    els.analysis.classList.remove("hidden");els.resultTitle.textContent="جارٍ التحليل…";els.confidence.textContent="—";els.confidence.className="gray";els.progress.style.width="12%";els.resultWords.innerHTML="";els.issues.innerHTML="";els.analysis.scrollIntoView({behavior:"smooth",block:"start"});
    await delay(80);els.progress.style.width="38%";
    try{
      let ref=null;if(referenceSource==="custom"&&referenceAudio){const decoded=await decodeBlob(referenceAudio);ref=C.makeReference(decoded.pcm,decoded.rate);}else if(referenceSource==="built-in"){const slice=builtInSlice();if(slice)ref=C.makeReference(slice.pcm,slice.rate);}
      await delay(40);els.progress.style.width="68%";
      const result=C.analyze(pcm,rate,ayah(),ref);await delay(80);els.progress.style.width="100%";lastResult=result;renderResult(result);saveAttempt(result);renderHistory();
    }catch(err){lastResult={accepted:false,overall:"undecidable",title:"حدث خطأ أثناء التحليل",message:err.message||String(err),issues:[{title:"تعذر إكمال التحليل",text:"أغلق أي تطبيق يستخدم الميكروفون ثم أعد المحاولة.",status:"undecidable"}],words:[]};renderResult(lastResult);}
  }
  function renderResult(r){
    els.resultTitle.textContent=r.title;els.confidence.textContent=r.accepted?`${Math.round(r.confidence*100)}٪ ثقة`:"لم يُقبل";els.confidence.className=r.overall==="pass"?"green":r.overall==="review"?"yellow":r.overall==="fail"?"red":"gray";
    els.recordStatus.textContent=r.accepted?"تم التحليل على الجهاز ولم يُرفع التسجيل.":(r.message||"أعد المحاولة.");
    els.resultWords.innerHTML="";(r.words||[]).forEach(w=>{const s=document.createElement("span");s.className=`word ${w.status}`;s.textContent=w.word;s.tabIndex=0;s.onclick=()=>showWord(w);s.onkeydown=e=>{if(e.key==="Enter")showWord(w);};els.resultWords.appendChild(s);els.resultWords.appendChild(document.createTextNode(" "));});
    els.issues.innerHTML="";(r.issues||[]).forEach(i=>{const d=document.createElement("div");d.className="issue";d.innerHTML=`<b>${escapeHtml(i.title)}${i.word?` — ${escapeHtml(i.word)}`:""}</b><p>${escapeHtml(i.text)}</p>`;els.issues.appendChild(d);});
    els.debugOutput.textContent=JSON.stringify(r,null,2);setTimeout(()=>els.progress.style.width="0",600);
  }
  function showWord(w){
    els.detailWord.textContent=w.word;const madd=(w.madd||[]).map(m=>`<div><small>${escapeHtml(m.name)} (${m.targetHarakat} حركات)</small><b>${escapeHtml(labels[m.status])} · ${Math.round(m.confidence*100)}٪</b><p>${escapeHtml(m.explanation)}</p></div>`).join("");
    els.detailContent.innerHTML=`<p>${escapeHtml(w.reason)}</p><div class="detail-grid"><div><small>الزمن المحاذى</small><b>${w.startMs}–${w.endMs} ms</b></div><div><small>ثقة الموضع</small><b>${Math.round(w.confidence*100)}٪</b></div><div><small>الفونيمات المتوقعة</small><b dir="ltr">${escapeHtml((w.expectedPhonemes||[]).join(" · "))}</b></div><div><small>الفونيمات المرصودة</small><b>غير مدعوم في النسخة التجريبية</b></div></div>${madd}`;
    els.detailDialog.showModal();
  }

  function getHistory(){try{return JSON.parse(localStorage.getItem("tajweed-attempts")||"[]");}catch(_){return[];}}
  function saveAttempt(r){const h=getHistory();h.unshift({time:Date.now(),ayah:selected,accepted:!!r.accepted,overall:r.overall,confidence:r.confidence||0,title:r.title,mode:r.mode||"rejected",speechMs:r.speechMs||0});localStorage.setItem("tajweed-attempts",JSON.stringify(h.slice(0,20)));}
  function renderHistory(){const h=getHistory();if(!h.length){els.history.innerHTML='<div class="history-empty">لم تُحفظ محاولات بعد.</div>';return;}els.history.innerHTML=h.slice(0,6).map(x=>`<div class="history-item"><div><b>${x.ayah===0?"الفاتحة كاملة":"الآية "+x.ayah} · ${escapeHtml(x.title)}</b><small>${new Date(x.time).toLocaleString("ar-EG")}</small></div><span class="${x.overall==="pass"?"green":x.overall==="review"?"yellow":x.overall==="fail"?"red":"gray"}">${Math.round(x.confidence*100)}٪</span></div>`).join("");}
  $("clearHistory").onclick=()=>{if(confirm("هل تريد مسح سجل المحاولات المحلي؟")){localStorage.removeItem("tajweed-attempts");renderHistory();}};
  $("retryButton").onclick=()=>{els.analysis.classList.add("hidden");els.record.scrollIntoView({behavior:"smooth",block:"center"});};
  $("detailsButton").onclick=()=>openDebug();$("debugToggle").onclick=()=>openDebug();$("debugClose").onclick=()=>closeDebug();
  function openDebug(){els.debug.classList.add("open");els.debug.setAttribute("aria-hidden","false");}
  function closeDebug(){els.debug.classList.remove("open");els.debug.setAttribute("aria-hidden","true");}
  function toast(message){els.recordStatus.textContent=message;}
  function delay(ms){return new Promise(r=>setTimeout(r,ms));}
  function escapeHtml(s){return String(s==null?"":s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));}
  window.addEventListener("beforeunload",cleanupAudio);initTabs();renderSelection();
})();
