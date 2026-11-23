/* Тема приложения krisa37 для встроенных страниц (/pad/, /show/).
 * Приложение открывает страницу с ?app=1&t=<тема>&fg=..&dim=..&line=..&ink=..&panel=..&bg=..&on=c1,c2,..&tr=1
 * Цвета — hex без «#» (panel может быть с альфой RRGGBBAA). tr=1 — прозрачный фон (видно фон приложения). */
(function () {
  var q = new URLSearchParams(location.search);
  if (q.get('app') !== '1') return;
  var t = q.get('t') || 'terminal';
  var root = document.documentElement, st = root.style;
  function hx(k, d) { var v = q.get(k); return v && /^[0-9a-fA-F]{6,8}$/.test(v) ? '#' + v : d; }
  var fg = hx('fg', '#EDEDED');
  st.setProperty('--fg', fg);
  st.setProperty('--dim', hx('dim', '#8A8A8A'));
  st.setProperty('--line', hx('line', '#5C5C5C'));
  st.setProperty('--ink', hx('ink', '#000000'));
  st.setProperty('--panel', hx('panel', '#000000'));
  st.setProperty('--bg', hx('bg', '#000000'));
  var on = (q.get('on') || '').split(',').filter(function (c) { return /^[0-9a-fA-F]{6}$/.test(c); }).map(function (c) { return '#' + c; });
  st.setProperty('--on', on.length > 1 ? 'linear-gradient(90deg,' + on.join(',') + ')' : (on[0] || fg));
  var F = {
    terminal: ['VT323', 'ui-monospace,Menlo,Consolas,monospace', '0px'],
    thermal: ['VT323', 'ui-monospace,Menlo,Consolas,monospace', '0px'],
    blueprint: ['Manrope', 'Manrope,system-ui,sans-serif', '0px'],
    prism: ['Unbounded', 'Manrope,system-ui,sans-serif', '16px']
  }[t] || ['VT323', 'ui-monospace,monospace', '0px'];
  st.setProperty('--tfont', F[0] + ',ui-monospace,monospace');
  st.setProperty('--bfont', F[1]);
  st.setProperty('--radius', F[2]);
  root.classList.add('app', 't-' + t);
  if (q.get('tr') === '1') root.classList.add('tr');

  var css = [
    "@font-face{font-family:VT323;src:url(../vt323.ttf)}",
    "@font-face{font-family:Manrope;src:url(../manrope.ttf)}",
    "@font-face{font-family:Unbounded;src:url(../unbounded.ttf)}",
    "html.app,html.app body{background:var(--bg)!important;color:var(--fg)!important;font-family:var(--bfont)!important}",
    "html.app.tr,html.app.tr body{background:transparent!important}",
    "html.app h1,html.app #back,html.app .wrap>p.dim:first-child{display:none!important}",
    "html.app .wrap{padding-top:6px!important}",
    "html.app .dim,html.app .st{color:var(--dim)!important}",
    "html.app a{color:var(--fg)!important}",
    "html.app .card,html.app .ghost,html.app .tab,html.app .snd,html.app .item,html.app input,html.app .pad>.snd{background:var(--panel)!important;border:1px solid var(--line)!important;color:var(--fg)!important;border-radius:var(--radius)!important;box-shadow:none!important}",
    "html.app .big,html.app .tab.on,html.app .snd:active,html.app button.big{background:var(--on)!important;color:var(--ink)!important;border:1px solid var(--fg)!important;border-radius:var(--radius)!important}",
    "html.app .big,html.app .tab,html.app .ghost,html.app button{font-family:var(--tfont)!important;letter-spacing:.5px}",
    "html.app .tab{font-size:22px!important}",
    "html.app input::placeholder{color:var(--dim)!important}",
    "html.app #prog,html.app #prog>i{border-radius:0!important}html.app #prog{background:var(--line)!important}html.app #prog>i{background:var(--fg)!important}",
    "html.app .dot.on{background:var(--fg)!important}",
    /* Терминал / Тепловизор: строгие рамки без скруглений, моноширинный текст */
    "html.t-terminal .big,html.t-thermal .big{text-transform:none}",
    "html.t-thermal .card,html.t-thermal .ghost,html.t-thermal .tab,html.t-thermal .snd,html.t-thermal input{border-color:var(--line)!important}",
    /* Призма: матовое стекло с радужной кромкой */
    "html.t-prism .card,html.t-prism .ghost,html.t-prism .tab,html.t-prism .snd,html.t-prism .item,html.t-prism input{backdrop-filter:blur(14px) saturate(140%);-webkit-backdrop-filter:blur(14px) saturate(140%);border-color:rgba(255,255,255,.18)!important}",
    "html.t-prism .big,html.t-prism .tab.on{box-shadow:0 0 18px rgba(139,108,255,.35)!important;border-color:transparent!important}",
    /* Чертёж: тонкие белые линии, миллиметровка остаётся видна сквозь панели */
    "html.t-blueprint .big,html.t-blueprint .tab.on{border-color:var(--fg)!important}"
  ].join('\n');
  var s = document.createElement('style'); s.textContent = css;
  (document.head || root).appendChild(s);
})();
