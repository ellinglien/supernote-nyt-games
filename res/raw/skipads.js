/* Injected by the NYT Games app on every page. With the ad itself blocked, NYT still shows an
   empty "Advertisement" screen before some games, with a "Continue to <game>" button: press
   it. Matches only NYT's ad-interstitial skip button. Idempotent. */
(function () {
  if (window.__einkSkipAds) return;
  window.__einkSkipAds = true;
  var tries = 0;
  var timer = setInterval(function () {
    var skip = document.querySelector('[class*=adInterstitialSkipButton]');
    if (skip && !skip.disabled) skip.click();
    if (++tries > 120) clearInterval(timer); // the first minute after a page load
  }, 500);
})();
