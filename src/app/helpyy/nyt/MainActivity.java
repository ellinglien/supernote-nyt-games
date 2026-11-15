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
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebViewClient;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.HashMap;
import java.util.Map;
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
    /**
     * The hint pages the games' lightbulb buttons open (Wordle Review, Connections Companion,
     * Spelling Bee forum, Wordplay) are dated articles in the crosswords section, e.g.
     * /2026/10/14/crosswords/wordle-review-1944.html. Only these: other dated pages are news,
     * and the index pages and columns stay blocked too. back.js adds a way back.
     */
    static final Pattern HINT_PAGE = Pattern.compile("^/\\d{4}/\\d{2}/\\d{2}/crosswords/");
    /**
     * WordleBot and Connections Bot, which "Did you beat the bot?" opens after a game
     * (/interactive/2022/upshot/wordle-bot.html, /interactive/2024/upshot/connections-bot.html).
     * They used to be blocked, and the link landed on the main puzzle page (the owner, 2026-11-15).
     */
    static final Pattern BOT_PAGE = Pattern.compile("^/interactive/\\d{4}/upshot/[a-z-]+-bot\\.html$");
    /** Connections: Sports Edition, in NYT's games menu. Its puzzles come from The Athletic's hosts. */
    static final String SPORTS_EDITION = "/athletic/connections-sports-edition";
    /** Crossplay only plays in NYT's phone app ("Download app"): its links are hidden (eink.css) and its page blocked. */
    static final String CROSSPLAY = "/games/crossplay";
    private static final String[] ASSET_HOSTS = {
        "nytimes.com", "nyt.com",
        // Connections: Sports Edition's puzzles and pictures
        "theathletic.com",
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
        // NYT's news-site service worker (/vi-assets/static-assets/sw.js) ran before every page
        // load; starting it on the Manta took about 8 s (2026-11-15). The games don't need it:
        // nothing a service worker asks for is fetched, so none installs, and LockedClient
        // removes the one already installed.
        ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest req) { return blocked(); }
        });
        web = new WebView(this);
        // A loading screen until NYT's page first draws (the owner, 2026-11-15: "loading screen
        // takes about a minute before anything appears. can we include a loading screen").
        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        root.addView(splash(), new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

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

        web.setWebViewClient(new LockedClient(new File(getCacheDir(), "patched-v4"),
                readRaw(R.raw.clues) + "\n" + readRaw(R.raw.back) + "\n" + readRaw(R.raw.skipads),
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

    /** The app's crossword-grid icon, "NYT Games" and "Loading…" on white, centred. */
    private View splash() {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.WHITE);
        box.setClickable(true); // nothing under it is tapped while it shows
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        box.addView(icon, new LinearLayout.LayoutParams((int) (200 * dp), (int) (200 * dp)));
        TextView name = new TextView(this);
        name.setText("NYT Games");
        name.setTextColor(Color.BLACK);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 40);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, (int) (32 * dp), 0, (int) (12 * dp));
        box.addView(name);
        TextView loading = new TextView(this);
        loading.setText("Loading…");
        loading.setTextColor(Color.DKGRAY);
        loading.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        loading.setGravity(Gravity.CENTER);
        box.addView(loading);
        splash = box;
        return box;
    }

    /** The loading screen, until the first page draws; then null. */
    private View splash;

    static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "utf-8", 204, "Blocked", null, new ByteArrayInputStream(new byte[0]));
    }

    /** Removes service workers already installed (NYT's news-site one); see onCreate. */
    private static final String NO_SERVICE_WORKERS = "navigator.serviceWorker && navigator.serviceWorker.getRegistrations()"
        + ".then(function (rs) { rs.forEach(function (r) { r.unregister(); }); });";

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
        if (path.startsWith(CROSSPLAY)) return false;
        for (String p : PAGE_PATHS) if (path.startsWith(p)) return true;
        if (path.startsWith(SPORTS_EDITION)) return true;
        return HINT_PAGE.matcher(path).find() || BOT_PAGE.matcher(path).matches();
    }

    /** NYT's home page, where its login sometimes lands. */
    static boolean isHome(Uri u) {
        String path = u.getPath();
        return path == null || path.isEmpty() || "/".equals(path);
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

    /**
     * The games-assets files kept on the tablet, by extension, with their types. Scripts and
     * styles are patched (dynamic viewport units; eink.css). Pictures and fonts are kept too
     * (the owner, 2026-11-15: "save any images on first load so it keeps stuff in cache"):
     * NYT lets the WebView keep its icons for only 5 minutes, so nearly every start fetched
     * about 45 of them again.
     */
    private static final Map<String, String> KEPT_TYPES = new HashMap<>();
    static {
        KEPT_TYPES.put("css", "text/css"); KEPT_TYPES.put("js", "application/javascript");
        KEPT_TYPES.put("svg", "image/svg+xml"); KEPT_TYPES.put("png", "image/png");
        KEPT_TYPES.put("jpg", "image/jpeg"); KEPT_TYPES.put("jpeg", "image/jpeg");
        KEPT_TYPES.put("webp", "image/webp"); KEPT_TYPES.put("gif", "image/gif");
        KEPT_TYPES.put("woff2", "font/woff2"); KEPT_TYPES.put("woff", "font/woff");
    }
    /** Pictures and fonts can change under the same name: fetched again after a week. Script and style names carry a content hash, so they never go stale. */
    private static final long REFRESH_MS = 7L * 24 * 60 * 60 * 1000;
    /** Kept files are deleted 60 days after they were fetched, so files NYT has stopped using don't pile up. */
    private static final long PRUNE_MS = 60L * 24 * 60 * 60 * 1000;

    static String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 || dot < path.lastIndexOf('/') ? "" : path.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    static boolean isKept(Uri u) {
        String path = u.getPath() == null ? "" : u.getPath();
        return "www.nytimes.com".equals(u.getHost()) && path.startsWith("/games-assets/") && KEPT_TYPES.containsKey(extension(path));
    }

    /** Crossplay's pictures (its icons and animation), blocked with its links. Its scripts still load, so NYT's pages don't break. */
    static boolean isCrossplayPicture(Uri u) {
        String path = u.getPath() == null ? "" : u.getPath().toLowerCase(java.util.Locale.ROOT);
        return path.contains("crossplay") && !path.endsWith(".js") && !path.endsWith(".css");
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
            new Thread(new Runnable() {
                @Override public void run() { prune(); }
            }, "prune").start();
        }

        /** Deletes kept files older than PRUNE_MS, and temporary files left by a download cut short. */
        private void prune() {
            File[] files = cache.listFiles();
            if (files == null) return;
            long now = System.currentTimeMillis();
            for (File f : files) {
                if (f.getName().endsWith(".tmp") ? now - f.lastModified() > 60_000 : now - f.lastModified() > PRUNE_MS) f.delete();
            }
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
            if (url != null && url.startsWith("https://")) view.evaluateJavascript(NO_SERVICE_WORKERS, null);
        }

        /** The first page has drawn (or the offline page): the loading screen goes. */
        @Override
        public void onPageCommitVisible(WebView view, String url) {
            if (splash == null) return;
            ((ViewGroup) splash.getParent()).removeView(splash);
            splash = null;
        }

        /** A kept games-assets file (isKept), from the tablet when it has it; null lets the WebView fetch it itself. */
        private WebResourceResponse kept(Uri u) {
            String path = u.getPath();
            String ext = extension(path);
            boolean text = ext.equals("css") || ext.equals("js");
            File f = new File(cache, path.replace('/', '_'));
            if (!f.exists() || (!text && System.currentTimeMillis() - f.lastModified() > REFRESH_MS)) {
                try {
                    fetch(u, f, text);
                } catch (Exception e) {
                    Log.w(TAG, "fetch failed for " + path + ": " + e);
                    if (!f.exists()) return null; // an older copy is better than none
                }
            }
            try {
                InputStream in = new FileInputStream(f);
                if (ext.equals("css")) in = new SequenceInputStream(in, new ByteArrayInputStream(einkCss));
                return new WebResourceResponse(KEPT_TYPES.get(ext), text ? "utf-8" : null, in);
            } catch (Exception e) {
                return null;
            }
        }

        /** Downloads u into f, the dynamic viewport units rewritten in scripts and styles. */
        private void fetch(Uri u, File f, boolean text) throws Exception {
            HttpURLConnection c = (HttpURLConnection) new URL(u.toString()).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            byte[] body;
            try {
                if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[65536];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                    body = out.toByteArray();
                }
            } finally {
                c.disconnect();
            }
            if (text) body = NEW_VIEWPORT_UNITS.matcher(new String(body, StandardCharsets.UTF_8)).replaceAll("$1v$2").getBytes(StandardCharsets.UTF_8);
            // Requests run on several threads: each writes its own temporary file.
            File tmp = new File(cache, f.getName() + "." + Thread.currentThread().getId() + ".tmp");
            try (FileOutputStream w = new FileOutputStream(tmp)) {
                w.write(body);
            }
            if (!tmp.renameTo(f)) { tmp.delete(); throw new java.io.IOException("rename"); }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            if ("app-close".equals(u.getScheme())) { finish(); return true; }
            if (isPage(u)) return false;
            Log.i(TAG, "blocked page " + u.getHost() + u.getPath());
            // NYT often bounces to its home page after login; land on the
            // puzzles instead of leaving a spinner that never resolves.
            // Any other link stays where it is, with a note. Sending every
            // blocked link to the puzzles made WordleBot's link look broken
            // (the owner, 2026-11-15).
            if (hostIs(u.getHost(), "nytimes.com") && req.isForMainFrame() && (req.isRedirect() || isHome(u))) {
                view.loadUrl(view.getContext().getString(R.string.start_url));
            } else if (req.isForMainFrame() && req.hasGesture()) {
                Toast.makeText(MainActivity.this, "Not in this app", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            if (isCrossplayPicture(req.getUrl())) {
                return blocked();
            }
            if (isKept(req.getUrl())) return kept(req.getUrl());
            if (isAsset(req.getUrl())) return null;
            Log.i(TAG, "blocked asset " + req.getUrl().getHost() + req.getUrl().getPath());
            return blocked();
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
