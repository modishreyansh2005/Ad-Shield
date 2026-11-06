// AdShield YouTube / YouTube Music ad defuser.
// Injected at document start (before YouTube's own scripts) so ad data is stripped
// from player responses before the player ever sees it.
(function () {
  if (window.__adshieldInstalled) return;
  window.__adshieldInstalled = true;

  var AD_KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams'];

  function prune(obj, depth) {
    if (!obj || typeof obj !== 'object' || depth > 4) return obj;
    if (Array.isArray(obj)) {
      for (var j = 0; j < obj.length; j++) prune(obj[j], depth + 1);
      return obj;
    }
    for (var i = 0; i < AD_KEYS.length; i++) {
      if (AD_KEYS[i] in obj) {
        try { delete obj[AD_KEYS[i]]; } catch (e) {}
      }
    }
    if (obj.playerResponse) prune(obj.playerResponse, depth + 1);
    return obj;
  }

  // 1. Responses parsed from strings (XHR, inline JSON)
  try {
    var origParse = JSON.parse;
    JSON.parse = function () {
      return prune(origParse.apply(this, arguments), 0);
    };
  } catch (e) {}

  // 2. Responses read with fetch(...).json() (used for in-app navigation)
  try {
    var origJson = Response.prototype.json;
    Response.prototype.json = function () {
      return origJson.apply(this, arguments).then(function (obj) { return prune(obj, 0); });
    };
  } catch (e) {}

  // 3. Player response embedded in the first HTML page (var ytInitialPlayerResponse = {...})
  function trap(name) {
    var value = window[name];
    if (value) prune(value, 0);
    try {
      Object.defineProperty(window, name, {
        configurable: true,
        get: function () { return value; },
        set: function (v) { value = prune(v, 0); }
      });
    } catch (e) {}
  }
  trap('ytInitialPlayerResponse');

  // 4. Fallback for ads that still slip through: skip, or mute + fast-forward
  var mutedByUs = false;
  setInterval(function () {
    try {
      var skipBtn = document.querySelector(
        '.ytp-skip-ad-button, .ytp-ad-skip-button, .ytp-ad-skip-button-modern, .videoAdUiSkipButton, button.ytp-ad-skip-button-text');
      if (skipBtn) skipBtn.click();

      var adShowing = document.querySelector('.ad-showing, .ad-interrupting');
      var video = document.querySelector('video');
      if (video) {
        if (adShowing) {
          if (!video.muted) { video.muted = true; mutedByUs = true; }
          if (isFinite(video.duration) && video.duration > 0) video.currentTime = video.duration;
        } else if (mutedByUs) {
          video.muted = false;
          mutedByUs = false;
        }
      }

      var overlayClose = document.querySelector('.ytp-ad-overlay-close-button, .ytp-ad-feedback-dialog-close-button');
      if (overlayClose) overlayClose.click();
    } catch (e) {}
  }, 300);

  // 5. Hide banner / feed ad containers
  var css = '.ad-container, .ytp-ad-module, .ytp-ad-overlay-container, #player-ads, ' +
    'ytd-promoted-video-renderer, ytd-display-ad-renderer, ytd-ad-slot-renderer, ytd-statement-banner-renderer, ' +
    'ytd-banner-promo-renderer, ytm-promoted-sparkles-web-renderer, ytm-promoted-video-renderer, ' +
    'ytm-companion-ad-renderer, ad-slot-renderer, ytmusic-mealbar-promo-renderer ' +
    '{ display: none !important; }';
  function addStyle() {
    if (document.getElementById('adshield-style')) return;
    var style = document.createElement('style');
    style.id = 'adshield-style';
    style.textContent = css;
    (document.head || document.documentElement).appendChild(style);
  }
  if (document.documentElement) addStyle();
  document.addEventListener('DOMContentLoaded', addStyle);
})();
