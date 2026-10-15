/* Injected by the NYT Games app on every page. Adds a big bar at the bottom with a way back
   (the Supernote has no Back button of its own) on:
   - hint pages (dated articles in the crosswords section, the only articles the app opens);
   - Tiles, which depends on colour, with a note that it isn't recommended on e-ink.
   Idempotent. */
(function () {
  var hint = /^\/\d{4}\/\d{2}\/\d{2}\/crosswords\//.test(location.pathname);
  var tiles = /^\/puzzles\/tiles/.test(location.pathname);
  if (!hint && !tiles) return;
  if (document.getElementById('eink-back')) return;
  if (!document.body) { document.addEventListener('DOMContentLoaded', arguments.callee); return; }

  var bar = document.createElement('a');
  bar.id = 'eink-back';
  bar.href = '/crosswords';
  bar.textContent = hint ? '←  Back to the game'
    : 'Tiles needs colour and isn\u2019t recommended on e-ink.   ←  Back';
  bar.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:2147483647;display:block;'
    + 'padding:22px 0;background:#fff;border-top:3px solid #000;color:#000;text-align:center;'
    + 'font:700 ' + (hint ? 26 : 22) + 'px/1 nyt-franklin,Arial,sans-serif;text-decoration:none';
  bar.addEventListener('click', function (e) {
    // The game is the page we came from; with no history, the link's /crosswords is the way out.
    if (history.length > 1) { e.preventDefault(); history.back(); }
  });
  // The article's top ad slot stays as an empty 300px box once its ad is blocked.
  var css = document.createElement('style');
  css.textContent = '#top-wrapper{display:none!important}';
  function attach() {
    if (!css.isConnected) document.head.appendChild(css);
    if (bar.isConnected) return;
    document.body.appendChild(bar);
    document.body.style.paddingBottom = '90px';
  }
  attach();
  setInterval(attach, 1500); // NYT's page code can rebuild <body> after load, dropping the bar
})();

/* On every game page, a small "Games" button beside the game's own menu button, back to the
   main games page. Pinned, so it never moves a game's layout. */
(function () {
  if (!/^\/(games|puzzles|crosswords\/game)\//.test(location.pathname)) return;
  if (document.getElementById('eink-home')) return;
  if (!document.body) { document.addEventListener('DOMContentLoaded', arguments.callee); return; }
  var home = document.createElement('a');
  home.id = 'eink-home';
  home.href = '/crosswords';
  home.textContent = 'Games';
  home.style.cssText = 'position:fixed;top:9px;left:64px;z-index:2147483646;padding:7px 16px;'
    + 'background:#fff;border:2px solid #000;border-radius:20px;color:#000;text-decoration:none;'
    + 'font:700 18px/1 nyt-franklin,Arial,sans-serif';
  // While a game loads (and on a few pages), NYT shows its own "Games" logo link in the same
  // spot; step aside for it.
  function logoShowing() {
    var logo = document.querySelector('.pz-nav__logo');
    if (!logo) return false;
    var r = logo.getBoundingClientRect(), c = getComputedStyle(logo);
    // On screen: after loading, games slide NYT's bar up out of view (top < 0).
    return r.width > 0 && r.bottom > 4 && r.top < 60 && c.visibility !== 'hidden' && c.opacity !== '0';
  }
  function attach() {
    if (!home.isConnected) document.body.appendChild(home);
    home.style.display = logoShowing() ? 'none' : '';
  }
  attach();
  setInterval(attach, 1000); // NYT's page code can rebuild <body> after load
})();
