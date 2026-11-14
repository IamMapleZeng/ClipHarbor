package local.codex.xsavevideo;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;

final class Douyin {
    static final String PAGE = "https://www.douyin.com";
    private static final String PLAYER = "https://open.douyin.com/player/video?vid=";
    private static final Pattern VIDEO = Pattern.compile("/(?:share/)?video/([0-9]{15,25})/?");
    private static final Pattern SHORT = Pattern.compile("/([A-Za-z0-9_-]{2,40})/?");
    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CountDownLatch finished = new CountDownLatch(1);
    private final AtomicReference<String> mediaUrl = new AtomicReference<>();
    private volatile boolean closed;
    private WebView browser;
    private String videoId;

    static String videoId(URI uri) {
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null) return null;
        host = host.toLowerCase(Locale.ROOT);
        if (!host.equals("douyin.com") && !host.equals("www.douyin.com")
                && !host.equals("www.iesdouyin.com") && !host.equals("m.douyin.com")) return null;
        Matcher match = VIDEO.matcher(uri.getPath());
        return match.matches() ? match.group(1) : null;
    }

    static String shortCode(URI uri) {
        if (!"v.douyin.com".equalsIgnoreCase(uri.getHost())) return null;
        Matcher match = SHORT.matcher(uri.getPath());
        return match.matches() ? match.group(1) : null;
    }

    static boolean isMedia(URI uri) {
        String host = uri.getHost();
        String path = uri.getPath();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                || uri.getPort() != -1 || path == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.endsWith(".douyinvod.com") && path.contains("/video/tos/")
            && !path.endsWith(".m3u8") && !path.endsWith(".m4s");
    }

    String resolve(FrameLayout host, String videoId) throws IOException, InterruptedException {
        main.post(() -> open(host, videoId));
        try {
            if (!finished.await(40, TimeUnit.SECONDS) || mediaUrl.get() == null) {
                throw new IOException("抖音没有返回可下载的视频。请确认这是一条公开的普通视频。");
            }
            return mediaUrl.get();
        } finally {
            cancel();
        }
    }

    private void open(FrameLayout host, String id) {
        if (closed) return;
        videoId = id;
        browser = new WebView(host.getContext());
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(false);
        browser.getSettings().setMediaPlaybackRequiresUserGesture(false);
        browser.getSettings().setUserAgentString(UA);
        browser.getSettings().setUseWideViewPort(true);
        browser.setImportantForAccessibility(WebView.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        browser.setFocusable(false);
        browser.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                view.evaluateJavascript(WATCH_DETAIL, null);
            }

            @Override public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(WATCH_DETAIL, null);
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return !"https".equals(uri.getScheme()) || request.isForMainFrame()
                    && !uri.toString().equals(PLAYER + id)
                    && !uri.toString().equals(PAGE + "/video/" + id);
            }

            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                if ("GET".equals(request.getMethod())) consider(request.getUrl().toString());
                return null;
            }
        });
        host.addView(browser, 0, new FrameLayout.LayoutParams(-1, -1));
        browser.loadUrl(PLAYER + id);
        main.postDelayed(() -> {
            if (!closed && browser != null && mediaUrl.get() == null) browser.loadUrl(PAGE + "/video/" + id);
        }, 12000);
        main.postDelayed(this::inspectPlayer, 1000);
    }

    private void consider(String candidate) {
        if (closed) return;
        try {
            URI uri = URI.create(candidate);
            if (looksLikeVideo(uri) && mediaUrl.compareAndSet(null, candidate)) finished.countDown();
        } catch (IllegalArgumentException ignored) {}
    }

    private void inspectPlayer() {
        if (closed || browser == null || mediaUrl.get() != null) return;
        browser.evaluateJavascript(WATCH_DETAIL + ";window.__clipHarborReplay?.()", null);
        browser.evaluateJavascript(
            "(window.__clipHarborDouyin?.id==='" + videoId + "' ? window.__clipHarborDouyin.urls : [])"
                + ".concat(Array.from(document.querySelectorAll('video')).map(v=>v.currentSrc))"
                + ".concat(performance.getEntriesByType('resource').map(r=>r.name))"
                + ".filter(u=>u.includes('.douyinvod.com/'))", value -> {
            try {
                JSONArray urls = new JSONArray(value);
                for (int i = 0; i < urls.length(); i++) consider(urls.optString(i));
            } catch (JSONException ignored) {}
            if (!closed && mediaUrl.get() == null) main.postDelayed(this::inspectPlayer, 1000);
        });
    }

    // Read only the public detail response requested by the page's own player.
    private static final String WATCH_DETAIL = "(function(){"
        + "if(window.__clipHarborWatch)return;window.__clipHarborWatch=true;"
        + "function detail(u){try{const x=new URL(u,location.href);"
        + "return x.hostname.endsWith('.douyin.com')&&x.pathname==='/aweme/v1/web/aweme/detail/';}catch(e){return false;}}"
        + "function save(j){const d=j?.aweme_detail;if(!d?.video||d.images?.length)return;"
        + "window.__clipHarborDouyin={id:String(d.aweme_id),urls:d.video.play_addr?.url_list||[]};}"
        + "const fetch=window.fetch;window.fetch=function(...a){return fetch.apply(this,a).then(r=>{"
        + "if(detail(r.url))r.clone().json().then(save).catch(()=>{});return r;});};"
        + "const send=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.send=function(...a){"
        + "this.addEventListener('load',()=>{try{if(detail(this.responseURL))"
        + "save(this.responseType==='json'?this.response:JSON.parse(this.responseText));}catch(e){}},{once:true});"
        + "return send.apply(this,a);};"
        + "const detailUrls=new Set();window.__clipHarborReplay=function(){"
        + "for(const r of performance.getEntriesByType('resource')){"
        + "if(!detail(r.name)||detailUrls.has(r.name))continue;detailUrls.add(r.name);"
        + "fetch(r.name,{credentials:'include'}).then(r=>r.json()).then(save).catch(()=>{});}};"
        + "})();";

    private static boolean looksLikeVideo(URI uri) {
        if (!isMedia(uri)) return false;
        String query = uri.getRawQuery();
        return query == null || !query.contains("mime_type=audio")
            && !query.contains("mime_type=video_mp4_dash");
    }

    void cancel() {
        closed = true;
        finished.countDown();
        main.post(() -> {
            if (browser == null) return;
            browser.stopLoading();
            ((FrameLayout) browser.getParent()).removeView(browser);
            browser.destroy();
            browser = null;
        });
    }
}
