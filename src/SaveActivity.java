package local.codex.xsavevideo;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public final class SaveActivity extends Activity {
    private static final int GREEN = 0xff136649;
    private static final int INK = 0xff12291f;
    private static final int MUTED = 0xff40594e;
    private static final String ALBUM = "Movies/X Save Video/";
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\\p{Z}]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern POST = Pattern.compile("/(?:[A-Za-z0-9_]{1,50}/status(?:es)?|i/(?:web/)?status)/([0-9]{1,25})(?:/video/([1-4]))?/?", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIZE = Pattern.compile("/([0-9]{2,5})x([0-9]{2,5})/");
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList("x.com", "twitter.com", "www.x.com", "www.twitter.com", "mobile.twitter.com", "mobile.x.com"));
    private TextView title, detail;
    private ProgressBar progress;
    private Button action, close;
    private volatile boolean cancelled;
    private volatile HttpURLConnection active;
    private boolean downloading;
    private Uri saved;
    private Link current;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xfff1f7f3);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(26), dp(26), dp(26), dp(26));
        scroll.addView(box);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        setContentView(scroll);

        TextView brand = label("SAVE VIDEO  /  X", 14, GREEN);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setLetterSpacing(.12f);
        box.addView(brand);
        title = label("", 30, INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add(box, title, 24);
        detail = label("", 16, MUTED);
        detail.setLineSpacing(dp(3), 1);
        add(box, detail, 12);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(ColorStateList.valueOf(GREEN));
        progress.setIndeterminateTintList(ColorStateList.valueOf(GREEN));
        progress.setVisibility(View.GONE);
        add(box, progress, 24);
        action = new Button(this);
        action.setBackgroundTintList(ColorStateList.valueOf(GREEN));
        action.setTextColor(Color.WHITE);
        action.setAllCaps(false);
        action.setVisibility(View.GONE);
        add(box, action, 20);
        close = new Button(this);
        close.setText("关闭");
        close.setTextColor(INK);
        close.setAllCaps(false);
        close.setOnClickListener(v -> finish());
        add(box, close, 6);

        CharSequence value = Intent.ACTION_SEND.equals(getIntent().getAction())
            ? getIntent().getCharSequenceExtra(Intent.EXTRA_TEXT) : null;
        current = value == null ? null : parseLink(value.toString());
        if (current != null) start();
        else if (Intent.ACTION_SEND.equals(getIntent().getAction()))
            show("没有找到 X 帖子链接", "请在 X 的视频帖子里点「分享」→「Share via…」→「Save Video」。");
        else
            show("从 X 分享即可下载", "打开 X 的视频帖子，点「分享」→「Share via…」→「Save Video」。视频会保存到 Movies/X Save Video。"
                + "\n\n仅支持可公开解析且提供 MP4 的帖子。解析时会向 FxTwitter 发送帖子编号，无需 X 账号或登录资料。");
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String s, int sp, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); return t;
    }
    private void add(LinearLayout box, View v, int gap) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(gap); box.addView(v, p);
    }
    private void show(String heading, String message) {
        title.setText(heading); detail.setText(message);
    }
    private void button(String text, Runnable fun) {
        action.setText(text); action.setOnClickListener(v -> fun.run()); action.setVisibility(View.VISIBLE);
    }
    private void start() {
        if (downloading) return;
        downloading = true;
        cancelled = false;
        saved = null;
        show("正在查找视频…", "正在读取公开帖子的媒体信息。");
        action.setVisibility(View.GONE);
        close.setText("取消");
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        new Thread(() -> {
            int done = 0;
            try {
                List<String> urls = resolve(current);
                for (int i = 0; i < urls.size(); i++) {
                    checkCancelled();
                    int part = i + 1;
                    update(() -> {
                        show("正在下载视频…", "第 " + part + " / " + urls.size() + " 个视频");
                        progress.setIndeterminate(true);
                    });
                    int fileIndex = current.index == 0 ? part : current.index;
                    saved = store(urls.get(i), "X_" + current.id + "_" + fileIndex + ".mp4");
                    done++;
                }
                int count = done;
                update(() -> {
                    finishProgress();
                    show("已保存 " + count + " 个视频", "保存位置：Movies/X Save Video\n也可以在相册中查找。");
                    button("播放" + (count > 1 ? "最后一个视频" : "视频"), this::play);
                });
            } catch (Exception e) {
                int count = done;
                String reason = e instanceof UserError ? e.getMessage() : "连接或写入失败。请检查网络、VPN 和手机存储空间。";
                update(() -> {
                    finishProgress();
                    show("下载未完成", (count > 0 ? "已保存 " + count + " 个视频。\n" : "") + reason);
                    if (count == 0) button("重试", this::start);
                    else button("播放已保存的视频", this::play);
                });
            }
        }, "x-save-download").start();
    }
    private void finishProgress() {
        downloading = false; progress.setVisibility(View.GONE); close.setText("关闭");
    }
    private void update(Runnable run) {
        runOnUiThread(() -> { if (!cancelled && !isFinishing() && !isDestroyed()) run.run(); });
    }
    private void play() {
        if (saved == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(saved, "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (android.content.ActivityNotFoundException e) {
            detail.setText("请在相册或文件管理器中播放。\n保存位置：Movies/X Save Video");
        }
    }
    @Override protected void onDestroy() {
        cancelled = true;
        HttpURLConnection c = active;
        if (c != null) new Thread(c::disconnect, "x-save-cancel").start();
        super.onDestroy();
    }

    private static final class Link {
        final String id; final int index;
        Link(String id, int index) { this.id = id; this.index = index; }
    }
    private static Link parseLink(String text) {
        Matcher m = URL.matcher(text);
        while (m.find()) {
            try {
                URI uri = URI.create(m.group().replaceFirst("[)\\]}>.,;!，。！？）]+$", ""));
                String host = uri.getHost();
                if (host == null || !HOSTS.contains(host.toLowerCase(Locale.ROOT)) || uri.getUserInfo() != null) continue;
                Matcher p = POST.matcher(uri.getPath());
                if (p.matches()) return new Link(p.group(1), p.group(2) == null ? 0 : Integer.parseInt(p.group(2)));
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }
    private static final class UserError extends IOException {
        UserError(String s) { super(s); }
    }
    private void checkCancelled() throws IOException {
        if (cancelled) throw new IOException("cancelled");
    }
    private HttpURLConnection open(String url, String mime) throws IOException {
        checkCancelled();
        HttpURLConnection c = (HttpURLConnection) new java.net.URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", mime);
        active = c;
        if (cancelled) { release(c); throw new IOException("cancelled"); }
        return c;
    }
    private void release(HttpURLConnection c) {
        c.disconnect();
        if (active == c) active = null;
    }
    private List<String> resolve(Link link) throws Exception {
        HttpURLConnection c = open("https://api.fxtwitter.com/status/" + link.id, "application/json");
        JSONObject body;
        try {
            int code = c.getResponseCode();
            if (code == 401 || code == 403 || code == 404)
                throw new UserError("无法读取这条帖子。帖子可能已删除、受保护，或暂时无法公开解析。");
            if (code == 429) throw new UserError("解析服务请求过多，请稍后重试。");
            if (code != 200) throw new UserError("解析服务暂时不可用（HTTP " + code + "）。");
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                for (int n; (n = in.read(buffer)) != -1;) {
                    checkCancelled();
                    if (out.size() + n > 2_000_000) throw new UserError("帖子返回的数据过大。");
                    out.write(buffer, 0, n);
                }
                body = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { release(c); }

        JSONObject post = body.optJSONObject("tweet");
        if (post == null) post = body.optJSONObject("status");
        if (post == null || post.optBoolean("is_tombstone")) throw new UserError("未找到公开可用的帖子。");
        JSONObject media = post.optJSONObject("media");
        JSONArray items = media == null ? null : media.optJSONArray("all");
        boolean allMedia = items != null && items.length() > 0;
        if (!allMedia) items = media == null ? null : media.optJSONArray("videos");
        if (items == null) throw new UserError("这条帖子里没有可下载的视频。");
        List<String> result = new ArrayList<>();
        boolean hasVideo = false;
        for (int i = 0; i < items.length(); i++) {
            JSONObject video = items.optJSONObject(i);
            if (video == null || (link.index != 0 && link.index != i + 1)) continue;
            String kind = video.optString("type");
            if (!kind.equals("video") && !kind.equals("gif")) continue;
            hasVideo = true;
            String best = bestMp4(video);
            if (best != null && !result.contains(best)) result.add(best);
        }
        if (result.isEmpty()) throw new UserError(hasVideo ? "这个视频没有可用的 MP4 下载地址。" : "这条链接没有指向可下载的视频。");
        return result;
    }
    private static String bestMp4(JSONObject video) {
        String url = video.optString("url");
        if (!isMp4(url)) url = null;
        long top = url == null ? -1 : rank(url, 0);
        for (String key : new String[]{"formats", "variants"}) {
            JSONArray formats = video.optJSONArray(key);
            if (formats == null) continue;
            for (int i = 0; i < formats.length(); i++) {
                JSONObject form = formats.optJSONObject(i);
                if (form == null) continue;
                String candidate = form.optString("url");
                if (!isMp4(candidate)) continue;
                long score = rank(candidate, form.optLong("bitrate"));
                if (score > top) { top = score; url = candidate; }
            }
        }
        return url;
    }
    private static boolean isMp4(String value) {
        try {
            URI uri = URI.create(value);
            String p = uri.getPath();
            return "https".equals(uri.getScheme()) && "video.twimg.com".equalsIgnoreCase(uri.getHost())
                && uri.getUserInfo() == null && uri.getPort() == -1 && p != null && p.endsWith(".mp4")
                && !p.contains("/0/0/") && !p.contains("/init/");
        } catch (IllegalArgumentException e) { return false; }
    }
    private static long rank(String url, long rate) {
        Matcher m = SIZE.matcher(URI.create(url).getPath());
        long area = m.find() ? Long.parseLong(m.group(1)) * Long.parseLong(m.group(2)) : 0;
        return area * 100_000_000L + Math.max(0, Math.min(rate, 99_999_999));
    }

    private Uri store(String url, String filename) throws Exception {
        HttpURLConnection c = open(url, "video/mp4, application/octet-stream");
        Uri item = null;
        boolean published = false;
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new UserError("视频服务器没有返回完整文件（HTTP " + code + "）。");
            long length = c.getContentLengthLong();
            try (InputStream in = new BufferedInputStream(c.getInputStream(), 65536)) {
                byte[] head = new byte[8];
                new DataInputStream(in).readFully(head);
                if (head[4] != 'f' || head[5] != 't' || head[6] != 'y' || head[7] != 'p')
                    throw new UserError("服务器返回的内容不是可保存的 MP4。");
                checkCancelled();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, ALBUM);
                values.put(MediaStore.Video.Media.IS_PENDING, 1);
                item = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (item == null) throw new UserError("无法在相册中创建视频文件。");
                try (OutputStream out = getContentResolver().openOutputStream(item, "w")) {
                    if (out == null) throw new IOException("no output");
                    out.write(head);
                    byte[] buffer = new byte[65536];
                    long transferred = head.length;
                    int last = -1;
                    for (int n; (n = in.read(buffer)) != -1;) {
                        checkCancelled();
                        out.write(buffer, 0, n);
                        transferred += n;
                        if (length > 0) {
                            int pct = (int) Math.min(100, transferred * 100 / length);
                            if (pct != last) {
                                last = pct;
                                update(() -> { progress.setIndeterminate(false); progress.setProgress(pct); });
                            }
                        }
                    }
                    if (length > 0 && transferred != length) throw new UserError("下载中断，视频文件没有保存。");
                }
            }
            checkCancelled();
            if (!hasVideoFrames(item)) throw new UserError("下载到的文件没有可播放的视频轨。");
            checkCancelled();
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Video.Media.IS_PENDING, 0);
            if (getContentResolver().update(item, ready, null, null) != 1) throw new IOException("publish failed");
            published = true;
            return item;
        } finally {
            release(c);
            if (!published && item != null) getContentResolver().delete(item, null, null);
        }
    }
    private boolean hasVideoFrames(Uri uri) {
        android.media.MediaExtractor extractor = new android.media.MediaExtractor();
        try {
            extractor.setDataSource(this, uri, null);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                String mime = extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    extractor.selectTrack(i);
                    return extractor.getSampleSize() > 0;
                }
            }
        } catch (IOException | RuntimeException ignored) {
        } finally { extractor.release(); }
        return false;
    }
}
