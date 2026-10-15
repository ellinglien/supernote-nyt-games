/* Injected by the Crossword app. NYT's phone layout leaves the space under
   the grid empty; this fills it with the current Across and Down clues in
   large type, the active direction in black and the crossing one in grey.
   Tapping a line selects that clue. Built only on NYT's stable xwd__ class
   names, and idempotent: safe to inject on every page load. */
(function () {
  if (window.__einkClues) return;
  // The app can inject this before the page has a <head> or <body>; wait for them.
  if (!document.head || !document.body) {
    document.addEventListener('DOMContentLoaded', arguments.callee);
    return;
  }
  window.__einkClues = true;

  var css = document.createElement('style');
  css.textContent =
    '#eink-clues{position:fixed;z-index:5;font-family:nyt-franklin,Arial,sans-serif;color:#000}' +
    '#eink-clues .row{display:flex;gap:14px;padding:14px 0;border-top:1px solid #999}' +
    '#eink-clues .row:first-child{border-top:2px solid #000}' +
    '#eink-clues .dir{flex:none;width:34px;font-weight:700;font-size:20px;line-height:30px}' +
    '#eink-clues .txt{font-size:24px;line-height:30px}' +
    '#eink-clues .off{color:#666}' +
    '#eink-clues .off .txt{white-space:nowrap;overflow:hidden;text-overflow:ellipsis;min-width:0}' +
    '#eink-clues .on .txt{font-weight:700}';
  document.head.appendChild(css);

  var panel = null;
  var PANEL_ROOM = 180; // px kept under the grid: the active clue on up to two lines, the crossing one on one
  var shown = ''; // what the panel last drew; e-ink redraws only when this changes

  function clueIn(list, title) {
    var li = list && (list.querySelector('.xwd__clue--selected') || list.querySelector('.xwd__clue--highlighted'));
    if (!li) return null;
    var label = li.querySelector('.xwd__clue--label'), text = li.querySelector('.xwd__clue--text');
    return {
      li: li,
      tag: (label ? label.textContent : '') + title.charAt(0),
      text: text ? text.textContent : '',
      active: li.classList.contains('xwd__clue--selected')
    };
  }

  function render() {
    // NYT's page code can rebuild <head> and <body> while loading, dropping what we added.
    if (!css.isConnected) document.head.appendChild(css);
    if (panel && !panel.isConnected) { panel = null; shown = ''; }
    var svg = document.querySelector('.xwd__board .xwd__svg') || document.querySelector('.xwd__svg');
    var lists = document.querySelectorAll('.xwd__clue-list--wrapper, .xwd__clue-list--wrapper-mobile');
    if (!svg || !lists.length) { if (panel) { panel.remove(); panel = null; shown = ''; } return; }

    var clues = [];
    for (var i = 0; i < lists.length; i++) {
      var t = lists[i].querySelector('.xwd__clue-list--title');
      var c = clueIn(lists[i], t ? t.textContent.trim() : '');
      if (c) clues.push(c);
    }
    if (!clues.length) return;

    // The svg box is taller than the grid it draws; the grid's real height
    // follows the viewBox aspect at the box's width.
    // With the clue lists hidden (EINK_CSS) the grid may take the whole width, but only as
    // much height as leaves room for this panel under it.
    var board = document.querySelector('.xwd__mobile_layout--board_container');
    var area = document.querySelector('.xwd__layout_puzzle--mobile');
    if (board && area) {
      var a = area.getBoundingClientRect();
      var side = Math.floor(Math.max(200, Math.min(a.width - 40, a.height - PANEL_ROOM))) + 'px';
      if (board.style.width !== side) board.style.width = side;
    }

    var b = svg.getBoundingClientRect(), vb = svg.viewBox && svg.viewBox.baseVal;
    var gridH = vb && vb.width ? Math.min(b.height, b.width * vb.height / vb.width) : Math.min(b.width, b.height);
    var controls = document.querySelector('.xwd__layout_controls--mobile');
    var bottom = controls ? controls.getBoundingClientRect().top : window.innerHeight;
    var top = b.top + gridH + 18;
    if (bottom - top < 80) { if (panel) panel.style.display = 'none'; return; }

    if (!panel) {
      panel = document.createElement('div');
      panel.id = 'eink-clues';
      document.body.appendChild(panel);
    }
    var key = [b.left, b.width, top, bottom].join() + clues.map(function (c) {
      return c.tag + c.text + c.active;
    }).join('|');
    if (key === shown && panel.style.display === '') return;
    shown = key;
    panel.style.display = '';
    panel.style.left = b.left + 'px';
    panel.style.width = b.width + 'px';
    panel.style.top = top + 'px';
    panel.style.maxHeight = (bottom - top - 12) + 'px';
    panel.style.overflow = 'hidden';

    panel.innerHTML = '';
    clues.forEach(function (c) {
      var row = document.createElement('div');
      row.className = 'row ' + (c.active ? 'on' : 'off');
      var dir = document.createElement('span'); dir.className = 'dir'; dir.textContent = c.tag;
      var txt = document.createElement('span'); txt.className = 'txt'; txt.textContent = c.text;
      row.appendChild(dir); row.appendChild(txt);
      row.addEventListener('click', function () { c.li.click(); });
      panel.appendChild(row);
    });
  }

  var queued = false;
  function schedule() {
    if (queued) return;
    queued = true;
    requestAnimationFrame(function () { queued = false; render(); });
  }

  new MutationObserver(function (records) {
    for (var i = 0; i < records.length; i++) {
      if (panel && panel.contains(records[i].target)) continue;
      schedule();
      return;
    }
  }).observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['class'] });
  window.addEventListener('resize', schedule);
  // Safety net for any change the observer misses (it once missed the puzzle appearing
  // after "Play"); cheap, since an unchanged panel isn't redrawn.
  setInterval(schedule, 1500);
  schedule();
})();
