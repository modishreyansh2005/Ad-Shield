package com.adshield.ui.player;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.adshield.data.PreferencesManager;
import com.adshield.data.db.AdShieldDbHelper;
import com.adshield.databinding.ActivityCyberPlayerBinding;
import com.adshield.dns.DomainBlocklist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * AdShield Cyber Player.
 * Runs Brave Browser's exact ad-blocking architecture inside an embedded Web engine:
 * 1. Pre-roll defusing scriptlet (strips adPlacements from JSON before playback).
 * 2. 0-second auto-skip & fast-forward scriptlet (video.currentTime = video.duration).
 * 3. Network request cancellation via DomainBlocklist (returns HTTP 200 empty responses).
 * 4. Injected CSS cosmetic rules (strips banner ads and video overlays).
 */
public class CyberPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_TARGET = "extra_target";
    public static final String TARGET_YOUTUBE = "youtube";
    public static final String TARGET_SPOTIFY = "spotify";
    public static final String TARGET_YTMUSIC = "ytmusic";

    private static final String URL_YOUTUBE = "https://m.youtube.com";
    private static final String URL_SPOTIFY = "https://open.spotify.com";
    private static final String URL_YTMUSIC = "https://music.youtube.com";
    private static final String YOUTUBE_SCRIPTLET_ASSET = "youtube_scriptlet.js";

    private String youtubeScriptlet = "";
    private boolean documentStartScriptInstalled = false;

    private ActivityCyberPlayerBinding binding;
    private DomainBlocklist domainBlocklist;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCyberPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        PreferencesManager prefs = new PreferencesManager(this);
        AdShieldDbHelper db = AdShieldDbHelper.getInstance(this);
        domainBlocklist = new DomainBlocklist(db, prefs);

        setupViews();
        setupWebView();
        setupBackNavigation();

        String target = getIntent().getStringExtra(EXTRA_TARGET);
        if (target == null) target = TARGET_YOUTUBE;
        loadTarget(target);
    }

    private void setupViews() {
        binding.btnCyberBack.setOnClickListener(v -> finish());
        binding.btnCyberReload.setOnClickListener(v -> binding.cyberWebView.reload());

        binding.btnTabYoutube.setOnClickListener(v -> loadTarget(TARGET_YOUTUBE));
        binding.btnTabSpotify.setOnClickListener(v -> loadTarget(TARGET_SPOTIFY));
        binding.btnTabYtmusic.setOnClickListener(v -> loadTarget(TARGET_YTMUSIC));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = binding.cyberWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // Modern Mobile Chrome User Agent for responsive player layout
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 AdShield/2.0");

        // The ad-stripping hooks must run before YouTube's own scripts read the player response;
        // onPageStarted/onPageFinished injection is too late, so it is only a fallback.
        youtubeScriptlet = readAsset(YOUTUBE_SCRIPTLET_ASSET);
        if (!youtubeScriptlet.isEmpty() && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(binding.cyberWebView, youtubeScriptlet,
                        Collections.singleton("https://*.youtube.com"));
                documentStartScriptInstalled = true;
            } catch (Exception ignored) {}
        }

        binding.cyberWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                binding.cyberProgressBar.setVisibility(View.VISIBLE);
                injectBraveScriptlets(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                binding.cyberProgressBar.setVisibility(View.GONE);
                injectBraveScriptlets(view, url);
            }

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    String host = request.getUrl().getHost();
                    if (host != null && domainBlocklist.isBlocked(host)) {
                        // Cancel request at the HTTP engine layer (Brave Logic)
                        return new WebResourceResponse("text/plain", "UTF-8",
                                new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        binding.cyberWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                binding.cyberProgressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    binding.cyberProgressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                binding.cyberFullscreenContainer.setVisibility(View.VISIBLE);
                binding.cyberFullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                binding.cyberWebView.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                binding.cyberFullscreenContainer.removeView(customView);
                binding.cyberFullscreenContainer.setVisibility(View.GONE);
                binding.cyberWebView.setVisibility(View.VISIBLE);
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                }
                customView = null;
                customViewCallback = null;
            }
        });
    }

    private void injectBraveScriptlets(WebView view, String url) {
        if (url == null) return;
        if (url.contains("youtube.com")) {
            // Script guards against double install, so re-running is harmless
            if (!documentStartScriptInstalled && !youtubeScriptlet.isEmpty()) {
                view.evaluateJavascript(youtubeScriptlet, null);
            }
        } else if (url.contains("spotify.com")) {
            String spotifyCss = "(function() { " +
                    "  var css = '.Root__ads-container, [data-testid=\"ad-display\"], [data-testid=\"leaderboard-ad\"], .ReactModalPortal, #banner-ad { display: none !important; }';" +
                    "  var style = document.createElement('style');" +
                    "  style.textContent = css;" +
                    "  if (document.head) document.head.appendChild(style);" +
                    "})();";
            view.evaluateJavascript(spotifyCss, null);
        }
    }

    private void loadTarget(String target) {
        switch (target) {
            case TARGET_SPOTIFY:
                binding.cyberWebView.loadUrl(URL_SPOTIFY);
                break;
            case TARGET_YTMUSIC:
                binding.cyberWebView.loadUrl(URL_YTMUSIC);
                break;
            case TARGET_YOUTUBE:
            default:
                binding.cyberWebView.loadUrl(URL_YOUTUBE);
                break;
        }
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return "";
        }
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (customView != null) {
                    // Exit fullscreen
                    WebChromeClient client = new WebChromeClient();
                    binding.cyberFullscreenContainer.removeView(customView);
                    binding.cyberFullscreenContainer.setVisibility(View.GONE);
                    binding.cyberWebView.setVisibility(View.VISIBLE);
                    if (customViewCallback != null) {
                        customViewCallback.onCustomViewHidden();
                    }
                    customView = null;
                    customViewCallback = null;
                } else if (binding.cyberWebView.canGoBack()) {
                    binding.cyberWebView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        binding.cyberWebView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.cyberWebView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding.cyberWebView.destroy();
    }
}
