package app.helpyy.nyt;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * NYT puzzles and nothing else. Pages may only be the games/crossword
 * sections or the NYT login; scripts, styles and images may come from NYT's
 * own hosts plus the bot-check services its login needs. Everything else
 * (ads, trackers, news articles, other sites) goes nowhere.
 */
public class MainActivity extends Activity {
    private static final String TAG = "NytLock";
    private static final String[] PAGE_PATHS = {"/crosswords", "/games", "/puzzles", "/svc/crosswords"};
    private static final String[] ASSET_HOSTS = {
        "nytimes.com", "nyt.com",
        // login bot checks
        "captcha-delivery.com", "datadome.co", "hcaptcha.com", "recaptcha.net",
    };

    private WebView web;
    /** The page that failed to load while offline, retried when the network returns; null when online. */
    private String failedUrl;
    /**
     * Waits for VALIDATED rather than onAvailable: right after Wi-Fi rejoins, DNS is often not up
     * yet, so an immediate reload fails again with NAME_NOT_RESOLVED and nothing retries it.
     */
    private final ConnectivityManager.NetworkCallback networkBack = new ConnectivityManager.NetworkCallback() {
        @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return;
            runOnUiThread(new Runnable() {
                @Override public void run() { retryFailed(); }
            });
        }
    };
    private boolean watchingNetwork;

    private void retryFailed() {
        if (failedUrl != null && !isDestroyed()) web.loadUrl(failedUrl);
    }

    /**
     * Shown instead of Chrome's error page, which has no way out on the Manta. Large type for
     * e-ink; "Try again" is an allowed NYT page, "Close" is handled in shouldOverrideUrlLoading.
     */
    private static final String OFFLINE_HTML = "<html><body style=\"font-family:sans-serif;margin:0;"
        + "display:flex;flex-direction:column;justify-content:center;align-items:center;height:100vh;color:#000\">"
        + "<p style=\"font-size:30px;margin:0 0 12px\">No connection</p>"
        + "<p style=\"font-size:20px;margin:0 0 48px;color:#444\">The puzzle reloads by itself when Wi-Fi is back.</p>"
        + "<a href=\"%s\" style=\"font-size:26px;color:#000;border:3px solid #000;border-radius:10px;"
        + "padding:18px 40px;text-decoration:none;margin-bottom:28px\">Try again</a>"
        + "<a href=\"app-close:\" style=\"font-size:22px;color:#000\">Close</a>"
        + "</body></html>";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        WebView.setWebContentsDebuggingEnabled(true); // inspect over USB from the Mac
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setGeolocationEnabled(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        // WebView follows the system font scale, which Supernote ships at 0.85; that shrank
        // every clue. Never go below 100%, but keep a larger scale someone picked.
        if (s.getTextZoom() < 100) s.setTextZoom(100);
        // Without "Mobile" in the user agent NYT serves its tablet layout (grid
        // beside the clue lists) instead of the phone one; the Manta's 1024px
        // CSS width is an iPad Pro's.
        s.setUserAgentString(s.getUserAgentString().replace(" Mobile", ""));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new LockedClient(new File(getCacheDir(), "patched-v4"), readRaw(R.raw.clues),
                "\n" + readRaw(R.raw.eink)));
        web.loadUrl(getString(R.string.start_url));

        // Wi-Fi often drops while the Manta sleeps; reload the failed page as soon as it is back.
        // A nicety only: if the system refuses (it did when the permission was missing), still open.
        try {
            ((ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE)).registerDefaultNetworkCallback(networkBack);
            watchingNetwork = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "no network callback: " + e);
        }
    }

    /** Coming back to the app is the other moment the network has usually returned. */
    @Override
    protected void onResume() {
        super.onResume();
        retryFailed();
    }

    private static boolean hostIs(String host, String domain) {
        return host != null && (host.equals(domain) || host.endsWith("." + domain));
    }

    static boolean isPage(Uri u) {
        if (!"https".equals(u.getScheme())) return false;
        String host = u.getHost();
        if ("myaccount.nytimes.com".equals(host)) return true;
        if (!"www.nytimes.com".equals(host) && !"nytimes.com".equals(host)) return false;
        String path = u.getPath() == null ? "" : u.getPath();
        for (String p : PAGE_PATHS) if (path.startsWith(p)) return true;
        return false;
    }

    static boolean isAsset(Uri u) {
        if (!"https".equals(u.getScheme())) return false;
        String host = u.getHost();
        for (String d : ASSET_HOSTS) if (hostIs(host, d)) return true;
        // Google's reCAPTCHA lives under these two paths only.
        String path = u.getPath() == null ? "" : u.getPath();
        if ("www.google.com".equals(host) && path.startsWith("/recaptcha")) return true;
        if ("www.gstatic.com".equals(host) && path.startsWith("/recaptcha")) return true;
        return false;
    }

    /**
     * The Manta's WebView is Chrome 96, which predates dynamic viewport units
     * (dvh/svh/lvh, Chrome 108). NYT sizes the puzzle screen with them, so the
     * rule is dropped, the layout grows to the full clue list, and the on-screen
     * keyboard ends up at the bottom of the document. Rewriting them to plain
     * vh/vw in NYT's own CSS and JS restores the fixed-height layout.
     */
    private static final Pattern NEW_VIEWPORT_UNITS = Pattern.compile("(\\d)[dsl]v([hw])\\b");

    static boolean isPatchable(Uri u) {
        String path = u.getPath() == null ? "" : u.getPath();
        return "www.nytimes.com".equals(u.getHost()) && path.startsWith("/games-assets/")
                && (path.endsWith(".css") || path.endsWith(".js"));
    }

    private String readRaw(int id) {
        try (InputStream in = getResources().openRawResource(id)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private class LockedClient extends WebViewClient {
        private final File cache;
        private final String cluesJs;
        /** res/raw/eink.css, added to every NYT games stylesheet as it is served (not cached). */
        private final byte[] einkCss;
        /** The current web load hit an error; its onPageFinished is the failure, not a recovery. */
        private boolean loadFailed;

        LockedClient(File cache, String cluesJs, String einkCss) {
            this.cache = cache;
            this.cluesJs = cluesJs;
            this.einkCss = einkCss.getBytes(StandardCharsets.UTF_8);
            cache.mkdirs();
        }

        /** The clue panel (res/raw/clues.js); it guards itself against double injection. */
        /** A page that cannot load at all shows our offline page instead of Chrome's dead end. */
        @Override
        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
            if (!req.isForMainFrame()) return;
            Log.i(TAG, "offline: " + error.getDescription());
            loadFailed = true;
            failedUrl = req.getUrl().toString();
            view.loadDataWithBaseURL(null, String.format(OFFLINE_HTML, failedUrl), "text/html", "utf-8", null);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (url != null && url.startsWith("http")) loadFailed = false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (url != null && url.startsWith("http") && !loadFailed && failedUrl != null) {
                failedUrl = null;
                view.clearHistory(); // so Back doesn't land on the offline page
            }
            if (!cluesJs.isEmpty()) view.evaluateJavascript(cluesJs, null);
        }

        /** games-assets filenames are content-hashed, so a cached copy never goes stale. */
        private WebResourceResponse patched(Uri u) {
            String path = u.getPath();
            String mime = path.endsWith(".css") ? "text/css" : "application/javascript";
            File f = new File(cache, path.replace('/', '_'));
            try {
                if (!f.exists()) {
                    HttpURLConnection c = (HttpURLConnection) new URL(u.toString()).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    if (c.getResponseCode() != 200) { c.disconnect(); return null; }
                    String body;
                    try (InputStream in = c.getInputStream()) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[65536];
                        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                        body = new String(out.toByteArray(), StandardCharsets.UTF_8);
                    }
                    body = NEW_VIEWPORT_UNITS.matcher(body).replaceAll("$1v$2");
                    File tmp = new File(cache, f.getName() + ".tmp");
                    try (FileOutputStream w = new FileOutputStream(tmp)) {
                        w.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                    tmp.renameTo(f);
                }
                InputStream in = new FileInputStream(f);
                if (path.endsWith(".css")) in = new SequenceInputStream(in, new ByteArrayInputStream(einkCss));
                return new WebResourceResponse(mime, "utf-8", in);
            } catch (Exception e) {
                Log.w(TAG, "patch failed for " + path + ": " + e);
                return null; // let the WebView fetch it unpatched
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            if ("app-close".equals(u.getScheme())) { finish(); return true; }
            if (isPage(u)) return false;
            Log.i(TAG, "blocked page " + u.getHost() + u.getPath());
            // NYT often bounces to its home page after login; land on the
            // puzzles instead of leaving a spinner that never resolves.
            if (hostIs(u.getHost(), "nytimes.com") && req.isForMainFrame()) {
                view.loadUrl(view.getContext().getString(R.string.start_url));
            }
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            if (isPatchable(req.getUrl())) return patched(req.getUrl());
            if (isAsset(req.getUrl())) return null;
            Log.i(TAG, "blocked asset " + req.getUrl().getHost() + req.getUrl().getPath());
            return new WebResourceResponse("text/plain", "utf-8", 204, "Blocked",
                    null, new ByteArrayInputStream(new byte[0]));
        }
    }

    @Override
    protected void onDestroy() {
        if (watchingNetwork) {
            ((ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(networkBack);
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
