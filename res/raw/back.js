/* Injected by the NYT Games app on every page. On a hint page (a dated article in the
   crosswords section, the only articles the app opens) it adds a big bar at the bottom that
   goes back to the game: the Supernote has no Back button of its own. Idempotent. */
(function () {
  if (!/^\/\d{4}\/\d{2}\/\d{2}\/crosswords\//.test(location.pathname)) return;
  if (document.getElementById('eink-back')) return;
  if (!document.body) { document.addEventListener('DOMContentLoaded', arguments.callee); return; }

  var bar = document.createElement('a');
  bar.id = 'eink-back';
  bar.href = '/crosswords';
  bar.textContent = '←  Back to the game';
  bar.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:2147483647;display:block;'
    + 'padding:22px 0;background:#fff;border-top:3px solid #000;color:#000;text-align:center;'
    + 'font:700 26px/1 nyt-franklin,Arial,sans-serif;text-decoration:none';
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
