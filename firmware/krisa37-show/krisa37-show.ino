/*
 * krisa37 «Шоу» — ESP32-S3-DevKitC-1 (N16R8): плата как локальный сервер показа.
 *
 * Плата создаёт свою Wi-Fi (точка доступа) и раздаёт веб-страницу + relay по WebSocket.
 * Доска/ТВ открывает в браузере http://192.168.4.1/ → «Экран» (чёрный экран, фото/видео).
 * Телефон открывает http://192.168.4.1/ → «Пульт» (саундпад, выбор фото/видео из галереи).
 * Команды и файлы идут ЧЕРЕЗ ПЛАТУ по локальной сети — без интернета, облака и VPN.
 *
 * Wi-Fi:  имя «krisa37-show», пароль «krisa3737».
 * Ограничение: фото и короткие клипы — ок; большое видео проходит медленно (плата — не сервер потокового видео).
 *
 * Сборка: arduino-cli, плата esp32:esp32:esp32s3, библиотека WebSockets (Links2004).
 */
#include <WiFi.h>
#include <WebServer.h>
#include <WebSocketsServer.h>

#define VERSION "show-1.0"
#define LED_PIN 48
static const char *AP_SSID = "krisa37-show";
static const char *AP_PASS = "krisa3737";      // не короче 8 символов

WebServer http(80);
WebSocketsServer ws(81);

void handleRoot();   // прототип: функция определена ниже, но нужна в setup()
void led(uint8_t r, uint8_t g, uint8_t b);

/* ---------- веб-страница (экран + пульт в одном файле) ---------- */
static const char PAGE[] PROGMEM = R"HTML(<!doctype html>
<html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>krisa37 Шоу</title>
<style>
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
html,body{margin:0;height:100%;background:#050806;color:#7dff9a;font:16px/1.5 system-ui,-apple-system,Roboto,sans-serif;overscroll-behavior:none}
.wrap{max-width:720px;margin:0 auto;padding:22px 16px 60px}
h1{font-size:30px;margin:0 0 4px}
.dim{color:#4c8a5b}
button{font:inherit;cursor:pointer}
.big{display:block;width:100%;font-size:20px;background:#7dff9a;color:#04140a;border:0;border-radius:10px;padding:18px;margin:10px 0}
.ghost{background:transparent;color:#7dff9a;border:1px solid #1f3b27;border-radius:10px;padding:14px}
.card{background:#0b120d;border:1px solid #1f3b27;border-radius:12px;padding:14px;margin:12px 0}
.row{display:flex;gap:8px}.row>*{flex:1}
.pad{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}
.snd{position:relative;background:#0b120d;border:1px solid #1f3b27;color:#7dff9a;border-radius:10px;padding:16px 8px;min-height:62px;font-size:14px;word-break:break-word}
.snd:active{background:#7dff9a;color:#04140a}.snd .x{position:absolute;top:2px;right:7px;color:#4c8a5b}
label.file input{display:none}
#screen{position:fixed;inset:0;background:#000;display:none}
#stage{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:#000}
#stage img,#stage video{max-width:100%;max-height:100%}
#sinfo{position:absolute;left:0;right:0;top:0;padding:16px;text-align:center;background:linear-gradient(#000c,#0000);transition:opacity .4s}
#sinfo.hide{opacity:0;pointer-events:none}
.st{font-family:ui-monospace,monospace;font-size:13px}
.dot{display:inline-block;width:9px;height:9px;border-radius:50%;background:#e05050;margin-right:6px}.dot.on{background:#22c55e}
#prog{height:6px;background:#1f3b27;border-radius:4px;overflow:hidden;margin-top:8px;display:none}#prog>i{display:block;height:100%;width:0;background:#7dff9a}
</style></head><body>

<div class="wrap" id="home">
<h1>krisa37 Шоу</h1>
<p class="dim">Плата раздаёт эту страницу локально. На доске/ТВ нажмите «Экран», на телефоне — «Пульт».</p>
<button class="big" onclick="goScreen()">▣ Экран (показывать здесь)</button>
<button class="big" onclick="goRemote()">✶ Пульт (управлять)</button>
<p class="dim" style="font-size:13px">Оба устройства подключены к Wi-Fi «krisa37-show». Интернета нет — звуки берутся из файлов телефона.</p>
</div>

<div id="screen">
  <div id="stage"></div>
  <div id="sinfo"><div class="dim">Экран готов</div><div class="st dim" id="sst">подключение…</div>
  <div class="dim" style="font-size:13px;margin-top:6px">нажмите, чтобы развернуть на весь экран</div></div>
</div>

<div class="wrap" id="remote" style="display:none">
<h1>Пульт</h1>
<div class="st"><span class="dot" id="dot"></span><span id="stat">подключение…</span></div>
<div class="card">
  <div class="dim" style="margin-bottom:8px">Показать на экране из галереи:</div>
  <label class="file"><span class="big" style="margin:0">🖼 Выбрать фото / видео</span>
    <input type="file" accept="image/*,video/*" onchange="pick(this,'media')"></label>
  <div id="prog"><i></i></div>
  <div class="row" style="margin-top:8px">
    <button class="ghost" onclick="cmd({t:'clear'})">✖ Убрать</button>
    <button class="ghost" onclick="cmd({t:'black'})">⬛ Чёрный</button>
  </div>
</div>
<div class="card">
  <div class="row" style="align-items:center;margin-bottom:8px">
    <div class="dim">Саундпад</div>
    <label class="file" style="flex:0 0 auto"><span class="ghost" style="padding:8px 12px">+ звук</span>
      <input type="file" accept="audio/*" onchange="addSound(this)"></label>
  </div>
  <div class="pad" id="pad"></div>
  <button class="ghost" style="margin-top:8px" onclick="cmd({t:'stopsound'})">⏹ Остановить звук</button>
  <p class="dim" style="font-size:12px">Интернета нет, поэтому звуки — это аудиофайлы с телефона. «+ звук» добавляет кнопку (на время сеанса).</p>
</div>
</div>

<script>
var ws, role='', stage=document.getElementById('stage'), cur=null, curUrl=null, sounds=[];
function connect(onopen){
  ws=new WebSocket('ws://'+location.hostname+':81/'); ws.binaryType='arraybuffer';
  ws.onopen=function(){ setStat(true); if(onopen)onopen(); };
  ws.onclose=function(){ setStat(false); setTimeout(function(){connect(onopen)},1500); };
  ws.onmessage=onMsg;
}
function setStat(on){ var d=document.getElementById('dot'),s=document.getElementById('stat'),ss=document.getElementById('sst');
  if(d){d.className='dot'+(on?' on':'');} if(s)s.textContent=on?'связь с платой есть':'нет связи'; if(ss)ss.textContent=on?'готов — ждём пульт':'нет связи с платой'; }
function send(x){ try{ if(ws&&ws.readyState===1){ ws.send(x); return true; } }catch(e){} return false; }
function cmd(o){ send(JSON.stringify(o)); }

/* ЭКРАН */
function goScreen(){ role='screen'; document.getElementById('home').style.display='none'; document.getElementById('screen').style.display='block';
  document.documentElement.requestFullscreen&&document.documentElement.requestFullscreen().catch(function(){});
  var info=document.getElementById('sinfo'); var t=setTimeout(function(){info.classList.add('hide')},6000);
  document.getElementById('screen').addEventListener('click',function(){ document.documentElement.requestFullscreen&&document.documentElement.requestFullscreen().catch(function(){});
    info.classList.remove('hide'); clearTimeout(t); t=setTimeout(function(){info.classList.add('hide')},6000); });
  connect();
}
function setStage(n){ if(curUrl){URL.revokeObjectURL(curUrl);curUrl=null;} stage.innerHTML=''; if(n)stage.appendChild(n); }
var audios=[];
function onMsg(e){
  if(typeof e.data==='string'){
    var d; try{d=JSON.parse(e.data)}catch(_){return;}
    if(d.t==='black'||d.t==='clear') setStage(null);
    else if(d.t==='stopsound'){ audios.forEach(function(a){try{a.pause()}catch(_){}}); audios=[]; }
    else if(d.t==='file-begin'){ cur={kind:d.kind,mime:d.mime,parts:[]}; document.getElementById('sinfo').classList.remove('hide'); document.getElementById('sst').textContent='приём файла…'; }
    else if(d.t==='file-end' && cur){
      var blob=new Blob(cur.parts,{type:cur.mime}); curUrl=URL.createObjectURL(blob);
      if(cur.kind==='audio'){ var a=new Audio(curUrl); a.play().catch(function(){}); audios.push(a); }
      else if(cur.kind==='video'){ var v=document.createElement('video'); v.src=curUrl; v.autoplay=true; v.loop=true; v.playsInline=true; v.setAttribute('playsinline',''); v.play().catch(function(){v.muted=true;v.play().catch(function(){})}); setStage(v); }
      else { var im=new Image(); im.src=curUrl; setStage(im); }
      cur=null; document.getElementById('sinfo').classList.add('hide');
    }
  } else if(cur){ cur.parts.push(e.data); }
}

/* ПУЛЬТ */
function goRemote(){ role='remote'; document.getElementById('home').style.display='none'; document.getElementById('remote').style.display='block'; renderPad(); connect(); }
function renderPad(){ var p=document.getElementById('pad'); p.innerHTML='';
  sounds.forEach(function(s,i){ var b=document.createElement('button'); b.className='snd'; b.textContent=s.name;
    b.onclick=function(){ sendFile(s.file,'audio'); };
    var x=document.createElement('span'); x.className='x'; x.textContent='×';
    x.onclick=function(e){ e.stopPropagation(); sounds.splice(i,1); renderPad(); };
    b.appendChild(x); p.appendChild(b); });
  if(!sounds.length){ var d=document.createElement('div'); d.className='dim'; d.style.cssText='grid-column:1/-1;font-size:13px'; d.textContent='Пусто. «+ звук» — добавьте аудиофайл с телефона.'; p.appendChild(d); }
}
function addSound(input){ var f=input.files&&input.files[0]; input.value=''; if(!f)return; sounds.push({name:(f.name||'звук').replace(/\.[^.]+$/,'').slice(0,20),file:f}); renderPad(); }
function pick(input,kind){ var f=input.files&&input.files[0]; input.value=''; if(f) sendFile(f, f.type.indexOf('video')===0?'video':'image'); }
async function sendFile(f,kind){
  if(!ws||ws.readyState!==1) return;
  cmd({t:'file-begin',kind:kind,mime:f.type||'application/octet-stream'});
  var buf=await f.arrayBuffer(), CH=8192, prog=document.getElementById('prog'), bar=prog?prog.firstElementChild:null;
  if(prog)prog.style.display='block';
  for(var o=0;o<buf.byteLength;o+=CH){
    var end=Math.min(o+CH,buf.byteLength); send(buf.slice(o,end));
    if(bar)bar.style.width=(end/buf.byteLength*100)+'%';
    while(ws.bufferedAmount>131072){ await new Promise(function(r){setTimeout(r,15)}); }
  }
  cmd({t:'file-end'});
  if(prog)setTimeout(function(){prog.style.display='none';bar.style.width='0'},600);
}
</script></body></html>)HTML";

/* ---------- relay: пересылаем кадр всем, кроме отправителя ---------- */
void onWs(uint8_t num, WStype_t type, uint8_t *payload, size_t len) {
  if (type != WStype_TEXT && type != WStype_BIN) return;
  for (uint8_t i = 0; i < 8; i++) {
    if (i == num) continue;
    if (type == WStype_TEXT) ws.sendTXT(i, payload, len);
    else ws.sendBIN(i, payload, len);
  }
}

void led(uint8_t r, uint8_t g, uint8_t b) { rgbLedWrite(LED_PIN, r, g, b); }

void setup() {
  Serial0.begin(115200);
  Serial0.printf("\n[krisa37-show] %s\n", VERSION);
  led(0, 0, 40);
  WiFi.mode(WIFI_AP);
  WiFi.softAP(AP_SSID, AP_PASS);
  Serial0.print("AP IP: "); Serial0.println(WiFi.softAPIP());   // 192.168.4.1
  http.on("/", handleRoot);
  http.onNotFound(handleRoot);        // любой путь — отдаём страницу
  http.begin();
  ws.begin();
  ws.onEvent(onWs);
  led(0, 40, 0);
}

void handleRoot() { http.send_P(200, "text/html", PAGE); }

void loop() {
  ws.loop();
  http.handleClient();
  // индикатор: зелёный — есть клиенты, синий — ждём
  static uint32_t t = 0;
  if (millis() - t > 500) {
    t = millis();
    if (ws.connectedClients() > 0) led(0, 40, 0);
    else led(0, 0, (millis() / 500) % 2 ? 40 : 0);
  }
}
