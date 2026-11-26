/* Сквозной тест «Шоу» и Sound pad: экран ↔ пульт с подменой серверов (MQTT через BroadcastChannel, ntfy — заглушка).
 * Запуск из корня репозитория:  python3 -m http.server 8765 &  node scripts/test-show.js */
const {chromium}=require('playwright');
const ROOT=require('path').resolve(__dirname,'..');
const BASE='http://localhost:8765/irbis/';
// поддельный MQTT: все страницы контекста связаны через BroadcastChannel (тот же код подписки/публикации)
const FAKE=`window.mqtt={connect:function(){var h={},bc=new BroadcastChannel('fake-mqtt'),sub=null,c={connected:false,
 on:function(e,f){(h[e]=h[e]||[]).push(f);if(e==='connect')setTimeout(function(){c.connected=true;f();},50);},
 subscribe:function(t){sub=t;},publish:function(t,m){bc.postMessage({t:t,m:m});}};
 bc.onmessage=function(e){if(sub===e.data.t)(h.message||[]).forEach(function(f){f(e.data.t,{toString:function(){return e.data.m}})});};return c;}};`;
let fails=0; const ok=(c,m)=>{console.log((c?'PASS ':'FAIL ')+m); if(!c) fails++;};
(async()=>{
 const b=await chromium.launch(); const ctx=await b.newContext();
 await ctx.addInitScript(FAKE);
 await ctx.route(/ntfy\.sh/, r=>{ const q=r.request();
   if(q.method()==='PUT') return r.fulfill({status:200,contentType:'application/json',headers:{'access-control-allow-origin':'*'},
     body:JSON.stringify({attachment:{url:BASE+'rat.png'}})});
   return r.abort(); });
 await ctx.route(/cdnjs.*mqtt/, r=>r.fulfill({status:200,contentType:'text/javascript',body:'/*stub*/'}));
 const errs=[];
 // 1) все страницы открываются без ошибок JS
 for(const u of ['','guide/','show/','show/test.html','sp/','pad/','flash/','pad/?app=1&t=prism&fg=FFFFFF&tr=1']){
   const p=await ctx.newPage(); const e=[]; p.on('pageerror',x=>e.push(x.message));
   const r=await p.goto(BASE+u,{waitUntil:'domcontentloaded'}).catch(x=>null); await p.waitForTimeout(700);
   ok(r&&r.status()===200&&!e.length, 'страница '+(u||'/')+(e.length?' — '+e[0]:'')); await p.close(); }
 for(const site of ['sp','show']){
   const scr=await ctx.newPage(), rem=await ctx.newPage(); [scr,rem].forEach(p=>p.on('pageerror',x=>errs.push(site+': '+x.message)));
   await scr.goto(BASE+site+'/'); await scr.evaluate(()=>goScreen()); await scr.waitForTimeout(300);
   const code=(await scr.textContent('#code')).trim(); ok(/^[A-Z2-9]{5}$/.test(code), site+': код экрана '+code);
   await rem.goto(BASE+site+'/#'+(site==='sp'?'r=':'remote=')+code); await rem.waitForTimeout(1500);
   // звук
   await scr.evaluate(()=>{ window._played=[]; const A=window.Audio; window.Audio=function(u){ _played.push(u); const a=new A(); a.play=()=>Promise.resolve(); return a; }; });
   await rem.locator('.snd').first().click(); await scr.waitForTimeout(400);
   const pl=await scr.evaluate(()=>_played); ok(pl.length===1 && /\.mp3/.test(pl[0]), site+': звук дошёл один раз ('+pl.length+')');
   // фото из файла (ntfy PUT → ссылка)
   const inp=rem.locator('input[type=file]').first();
   await inp.setInputFiles(ROOT+'/irbis/rat.png'); await scr.waitForTimeout(1500);
   const src=await scr.evaluate(()=>{const i=document.querySelector('#stage img');return i&&i.src}); ok(!!src, site+': фото показано ('+(src||'').slice(-20)+')');
   // убрать
   await rem.evaluate(s=>send({t:s==='sp'?'c':'clear'}),site); await scr.waitForTimeout(400);
   ok(await scr.evaluate(()=>!document.querySelector('#stage img')), site+': фото убрано');
   if(site==='sp'){ await rem.locator('.gi').first().click(); await scr.waitForTimeout(400);
     ok(await scr.evaluate(()=>!!document.querySelector('#stage img')), 'sp: фото из библиотеки'); }
   // запасной путь: ntfy недоступен → фото кусками
   await ctx.unroute(/ntfy\.sh/); await ctx.route(/ntfy\.sh/, r=>r.abort());
   await rem.evaluate(s=>send({t:s==='sp'?'c':'clear'}),site); await inp.setInputFiles(ROOT+'/irbis/iris.jpg'); await scr.waitForTimeout(6000);
   const s2=await scr.evaluate(()=>{const i=document.querySelector('#stage img');return i&&i.src.slice(0,23)}); ok(s2==='data:image/jpeg;base64,', site+': фото через запасной канал');
   await ctx.unroute(/ntfy\.sh/); await ctx.route(/ntfy\.sh/, r=>{ const q=r.request(); if(q.method()==='PUT') return r.fulfill({status:200,contentType:'application/json',headers:{'access-control-allow-origin':'*'},body:JSON.stringify({attachment:{url:BASE+'rat.png'}})}); return r.abort(); });
   await scr.close(); await rem.close();
 }
 ok(!errs.length,'нет ошибок JS'+(errs.length?': '+errs.join(' | '):''));
 console.log(fails?('ИТОГ: ошибок '+fails):'ИТОГ: всё прошло'); await b.close(); process.exit(fails?1:0);
})();
