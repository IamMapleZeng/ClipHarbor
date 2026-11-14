package local.codex.xsavevideo;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
    private static final int PAPER = 0xfff5f5ef;
    private static final int INK = 0xff142622;
    private static final int MUTED = 0xff54635c;
    private static final int MINT = 0xffbcf7da;
    private static final int CARD = 0xff092e2d;
    private static final int WHITE = 0xfff8fcf9;
    private static final int BORDER = 0xffd6ded6;
    private static final String X_ALBUM = "Movies/X Save Video";
    private static final String YOUTUBE_ALBUM = "Movies/YouTube Save Video";
    private static final String DOUYIN_ALBUM = "Movies/Douyin Save Video";
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\\p{Z}]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern POST = Pattern.compile("/(?:[A-Za-z0-9_]{1,50}/status(?:es)?|i/(?:web/)?status)/([0-9]{1,25})(?:/video/([1-4]))?/?", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIZE = Pattern.compile("/([0-9]{2,5})x([0-9]{2,5})/");
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList("x.com", "twitter.com", "www.x.com", "www.twitter.com", "mobile.twitter.com", "mobile.x.com"));
    private TextView title, detail, status;
    private ProgressBar progress;
    private Button action, close;
    private volatile boolean cancelled;
    private volatile HttpURLConnection active;
    private boolean downloading;
    private Uri saved;
    private Link current;
    private YouTube youtube;
    private FrameLayout root;
    private EditText douyinInput;
    private Button douyinButton;
    private Button selectAllButton, clearButton;
    private volatile Douyin douyin;
    private int keyboardHeight;
    private int scrollBeforeKeyboard;
    private Integer keyboardScrollTarget;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        youtube = new YouTube(getApplicationContext());
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAPER);
        scroll.setClipToPadding(false);
        FrameLayout content = new FrameLayout(this);
        scroll.addView(content);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        content.addView(box, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        content.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            int width = Math.min(r - l, dp(520));
            if (width > 0 && box.getLayoutParams().width != width) {
                box.getLayoutParams().width = width;
                box.requestLayout();
            }
        });
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                int bottom = Math.max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom);
                updateKeyboardScroll(scroll, bottom - bars.bottom);
                v.setPadding(bars.left, bars.top, bars.right, bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        scroll.getViewTreeObserver().addOnPreDrawListener(() -> {
            if (keyboardScrollTarget != null) {
                scroll.scrollTo(0, keyboardScrollTarget);
                keyboardScrollTarget = null;
            }
            return true;
        });
        if (Build.VERSION.SDK_INT < 30) {
            scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                private int fullHeight;

                @Override public void onLayoutChange(View v, int l, int t, int r, int b,
                                                       int oldL, int oldT, int oldR, int oldB) {
                    int height = b - t;
                    if (douyinInput == null || !douyinInput.hasFocus() || r - l != oldR - oldL) {
                        fullHeight = height;
                    }
                    updateKeyboardScroll(scroll, Math.max(0, fullHeight - height));
                }
            });
        }
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root = new FrameLayout(this);
        root.setFocusableInTouchMode(true);
        root.addView(scroll);
        setContentView(root);
        root.requestFocus();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        header.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView brand = label("ClipHarbor", 21, INK);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams brandLayout = new LinearLayout.LayoutParams(-2, -2);
        brandLayout.setMarginStart(dp(12));
        header.addView(brand, brandLayout);
        box.addView(header);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(24), dp(24), dp(26));
        panel.setBackground(surface(CARD, 26, 0));
        add(box, panel, 32);
        status = label("", 13, MINT);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(status);
        title = label("", 34, WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add(panel, title, 12);
        detail = label("", 15, 0xffd0e4dc);
        detail.setLineSpacing(dp(5), 1);
        add(panel, detail, 14);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(ColorStateList.valueOf(MINT));
        progress.setIndeterminateTintList(ColorStateList.valueOf(MINT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xff45625d));
        progress.setVisibility(View.GONE);
        add(panel, progress, 20);
        action = makeButton(MINT, INK, 0);
        action.setVisibility(View.GONE);
        add(box, action, 20);
        close = makeButton(PAPER, MUTED, BORDER);
        close.setText("关闭");
        close.setOnClickListener(v -> finish());
        add(box, close, 12);

        CharSequence value = Intent.ACTION_SEND.equals(getIntent().getAction())
            ? getIntent().getCharSequenceExtra(Intent.EXTRA_TEXT) : null;
        current = value == null ? null : parseLink(value.toString());
        if (current != null) start();
        else if (Intent.ACTION_SEND.equals(getIntent().getAction()))
            show("链接无法识别", "找不到视频链接", "请从视频页面分享，或在下方粘贴抖音链接。");
        else {
            show("X 与 YouTube", "把想看的视频\n存到手机", "打开公开视频，点「分享」，再选择 ClipHarbor。在 X 里先点「Share via…」。");
            TextView note = label("X → " + X_ALBUM + "\nYouTube → " + YOUTUBE_ALBUM
                + "\n抖音 → " + DOUYIN_ALBUM + "\n解析 X 链接时，FxTwitter 会收到帖子编号。", 13, MUTED);
            note.setLineSpacing(dp(4), 1);
            add(box, note, 20);
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(16), dp(18), dp(18));
        form.setBackground(surface(WHITE, 24, BORDER));
        TextView formTitle = label("抖音下载", 20, INK);
        formTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        form.addView(formTitle);
        douyinInput = new EditText(this);
        douyinInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        douyinInput.setGravity(Gravity.TOP | Gravity.START);
        douyinInput.setHint("粘贴抖音链接或整段分享文字");
        douyinInput.setHintTextColor(MUTED);
        douyinInput.setTextColor(INK);
        douyinInput.setTextSize(15);
        douyinInput.setMinLines(2);
        douyinInput.setMaxLines(4);
        douyinInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        douyinInput.setBackground(surface(PAPER, 14, BORDER));
        add(form, douyinInput, 12);
        LinearLayout editingActions = new LinearLayout(this);
        selectAllButton = makeButton(PAPER, INK, BORDER);
        selectAllButton.setText("全选");
        selectAllButton.setOnClickListener(v -> {
            douyinInput.requestFocus();
            douyinInput.selectAll();
            getSystemService(InputMethodManager.class).showSoftInput(douyinInput, InputMethodManager.SHOW_IMPLICIT);
        });
        editingActions.addView(selectAllButton, new LinearLayout.LayoutParams(0, -2, 1));
        clearButton = makeButton(PAPER, INK, BORDER);
        clearButton.setText("清空");
        clearButton.setOnClickListener(v -> {
            douyinInput.setError(null);
            douyinInput.getText().clear();
        });
        LinearLayout.LayoutParams clearLayout = new LinearLayout.LayoutParams(0, -2, 1);
        clearLayout.setMarginStart(dp(10));
        editingActions.addView(clearButton, clearLayout);
        add(form, editingActions, 10);
        douyinButton = makeButton(MINT, INK, 0);
        douyinButton.setText("下载抖音视频");
        douyinButton.setOnClickListener(v -> {
            if (downloading) return;
            Link link = parseLink(douyinInput.getText().toString(), true);
            if (link == null) {
                douyinInput.setError("请粘贴有效的抖音视频链接");
                return;
            }
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(douyinInput.getWindowToken(), 0);
            douyinInput.clearFocus();
            current = link;
            scrollBeforeKeyboard = 0;
            keyboardScrollTarget = null;
            scroll.smoothScrollTo(0, 0);
            start();
        });
        add(form, douyinButton, 10);
        add(box, form, 20);
        setFormEnabled(!downloading);
    }

    private void updateKeyboardScroll(ScrollView scroll, int height) {
        if (height == keyboardHeight) return;
        View content = scroll.getChildAt(0);
        if (keyboardHeight == 0 && douyinInput != null && douyinInput.hasFocus()) {
            scrollBeforeKeyboard = scroll.getScrollY();
            // Preserve enough scroll range even when the form fits on the screen before editing.
            content.setMinimumHeight(content.getHeight());
        }
        keyboardHeight = height;
        if (height == 0 || douyinInput != null && douyinInput.hasFocus()) {
            keyboardScrollTarget = scrollBeforeKeyboard + height;
        }
        if (height == 0) content.setMinimumHeight(0);
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String s, int sp, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); return t;
    }
    private void add(LinearLayout box, View v, int gap) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(gap); box.addView(v, p);
    }
    private GradientDrawable surface(int fill, int radius, int border) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (border != 0) shape.setStroke(dp(1), border);
        return shape;
    }
    private Button makeButton(int fill, int ink, int border) {
        Button button = new Button(this);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22092e2d),
            surface(fill, 17, border), null));
        button.setStateListAnimator(null);
        button.setMinHeight(dp(52));
        button.setPadding(dp(16), dp(10), dp(16), dp(10));
        button.setTextColor(ink);
        button.setTextSize(16);
        button.setAllCaps(false);
        return button;
    }
    private void show(String state, String heading, String message) {
        status.setText(state);
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
        setFormEnabled(false);
        show("正在解析", "查找视频…", current.douyin ? "正在读取抖音视频。"
            : current.youtube ? "正在向 YouTube 请求视频。" : "正在读取公开帖子的媒体信息。");
        action.setVisibility(View.GONE);
        close.setText("取消");
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        new Thread(() -> {
            int done = 0;
            try {
                if (current.douyin) {
                    saved = downloadDouyin(current);
                    done = 1;
                } else if (current.youtube) {
                    saved = downloadYouTube(current.id);
                    done = 1;
                } else {
                    List<String> urls = resolve(current);
                    for (int i = 0; i < urls.size(); i++) {
                        checkCancelled();
                        int part = i + 1;
                        update(() -> {
                            show("正在保存", "下载视频…", "第 " + part + " / " + urls.size() + " 个视频");
                            progress.setIndeterminate(true);
                        });
                        int fileIndex = current.index == 0 ? part : current.index;
                        saved = store(urls.get(i), "X_" + current.id + "_" + fileIndex + ".mp4");
                        done++;
                    }
                }
                int count = done;
                update(() -> {
                    finishProgress();
                    show("下载完成", "已保存 " + count + " 个视频", current.album() + "\n也可以在相册中查找。");
                    button("播放" + (count > 1 ? "最后一个视频" : "视频"), this::play);
                });
            } catch (Exception e) {
                int count = done;
                String reason = e instanceof UserError || e instanceof YouTube.DownloadError
                    ? e.getMessage() : "连接或写入失败。请检查网络、VPN 和手机存储空间。";
                update(() -> {
                    finishProgress();
                    show(count > 0 ? "部分视频已保存" : "下载失败", "下载未完成",
                        (count > 0 ? "已保存 " + count + " 个视频。\n" : "") + reason);
                    if (count == 0) button("重试", this::start);
                    else button("播放已保存的视频", this::play);
                });
            }
        }, "x-save-download").start();
    }
    private Uri downloadYouTube(String id) throws Exception {
        File directory = java.nio.file.Files.createTempDirectory(getCacheDir().toPath(), "youtube-").toFile();
        File video = new File(directory, "video.mp4");
        try {
            youtube.download(id, video, () -> cancelled, percent -> update(() -> {
                show("正在保存", "下载视频…", "YouTube · " + percent + "%");
                progress.setIndeterminate(false);
                progress.setProgress(percent);
            }));
            update(() -> {
                show("正在保存", "写入相册…", "正在检查并保存 MP4。");
                progress.setIndeterminate(true);
            });
            try (InputStream input = new BufferedInputStream(new FileInputStream(video))) {
                return saveMp4(input, video.length(), "YT_" + id + ".mp4", true);
            }
        } finally {
            File[] files = directory.listFiles();
            if (files != null) for (File file : files) file.delete();
            directory.delete();
        }
    }
    private Uri downloadDouyin(Link link) throws Exception {
        String id = link.id;
        if (!id.matches("[0-9]{15,25}")) {
            // The short-link redirect is inspected, never followed to an arbitrary host.
            HttpURLConnection c = open("https://v.douyin.com/" + id + "/", "text/html");
            try {
                c.setRequestProperty("User-Agent", Douyin.UA);
                c.getResponseCode();
                String location = c.getHeaderField("Location");
                id = location == null ? null : Douyin.videoId(URI.create(location));
            } finally { release(c); }
            if (id == null) throw new UserError("抖音短链接已失效，或没有指向普通视频。");
        }
        Douyin resolver = new Douyin();
        douyin = resolver;
        String source;
        try {
            checkCancelled();
            source = resolver.resolve(root, id);
        } catch (IOException e) {
            throw new UserError(e.getMessage());
        } finally { douyin = null; }
        update(() -> show("正在保存", "下载视频…", "正在保存抖音视频到相册。"));
        return store(source, "DY_" + id + ".mp4");
    }

    private void setFormEnabled(boolean enabled) {
        if (douyinButton == null) return;
        douyinButton.setEnabled(enabled);
        douyinInput.setEnabled(enabled);
        selectAllButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
    }
    private void finishProgress() {
        downloading = false; progress.setVisibility(View.GONE); close.setText("关闭");
        setFormEnabled(true);
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
            detail.setText("请在相册或文件管理器中播放。\n保存位置：" + current.album());
        }
    }
    @Override protected void onDestroy() {
        cancelled = true;
        youtube.cancel();
        Douyin resolver = douyin;
        if (resolver != null) resolver.cancel();
        HttpURLConnection c = active;
        if (c != null) new Thread(c::disconnect, "x-save-cancel").start();
        super.onDestroy();
    }

    private static final class Link {
        final String id; final int index; final boolean youtube; final boolean douyin;
        Link(String id, int index, boolean youtube, boolean douyin) {
            this.id = id; this.index = index; this.youtube = youtube; this.douyin = douyin;
        }
        String album() { return douyin ? DOUYIN_ALBUM : youtube ? YOUTUBE_ALBUM : X_ALBUM; }
    }
    private static Link parseLink(String text) {
        return parseLink(text, false);
    }
    private static Link parseLink(String text, boolean douyinOnly) {
        Matcher m = URL.matcher(text.replace("\\:", ":").replace("\\/", "/"));
        while (m.find()) {
            try {
                URI uri = URI.create(m.group().replaceFirst("[)\\]}>.,;!，。！？）]+$", ""));
                String host = uri.getHost();
                if (host == null || uri.getUserInfo() != null) continue;
                String dyId = Douyin.videoId(uri);
                if (dyId == null) dyId = Douyin.shortCode(uri);
                if (dyId != null) return new Link(dyId, 0, false, true);
                if (douyinOnly) continue;
                String youtubeId = YouTube.videoId(uri);
                if (youtubeId != null) return new Link(youtubeId, 0, true, false);
                if (!HOSTS.contains(host.toLowerCase(Locale.ROOT))) continue;
                Matcher p = POST.matcher(uri.getPath());
                if (p.matches()) return new Link(p.group(1), p.group(2) == null ? 0 : Integer.parseInt(p.group(2)), false, false);
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
        try {
            if (current.douyin) {
                c.setRequestProperty("User-Agent", Douyin.UA);
                c.setRequestProperty("Referer", Douyin.PAGE + "/");
            }
            int code = c.getResponseCode();
            if (code != 200) throw new UserError("视频服务器没有返回完整文件（HTTP " + code + "）。");
            try (InputStream in = new BufferedInputStream(c.getInputStream(), 65536)) {
                return saveMp4(in, c.getContentLengthLong(), filename, false);
            }
        } finally { release(c); }
    }

    private Uri saveMp4(InputStream in, long length, String filename, boolean requireAudio) throws Exception {
        Uri item = null;
        boolean published = false;
        try {
            byte[] head = new byte[8];
            new DataInputStream(in).readFully(head);
            if (head[4] != 'f' || head[5] != 't' || head[6] != 'y' || head[7] != 'p')
                throw new UserError("服务器返回的内容不是可保存的 MP4。");
            checkCancelled();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, current.album() + "/");
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
                    if (!requireAudio && length > 0) {
                        int pct = (int) Math.min(100, transferred * 100 / length);
                        if (pct != last) {
                            last = pct;
                            update(() -> { progress.setIndeterminate(false); progress.setProgress(pct); });
                        }
                    }
                }
                if (length > 0 && transferred != length) throw new UserError("下载中断，视频文件没有保存。");
            }
            checkCancelled();
            if (!hasPlayableTracks(item, requireAudio)) throw new UserError(requireAudio
                ? "下载的 MP4 缺少可播放的视频或音频。"
                : "下载到的文件没有可播放的视频轨。");
            checkCancelled();
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Video.Media.IS_PENDING, 0);
            if (getContentResolver().update(item, ready, null, null) != 1) throw new IOException("publish failed");
            published = true;
            return item;
        } finally {
            if (!published && item != null) getContentResolver().delete(item, null, null);
        }
    }
    private boolean hasPlayableTracks(Uri uri, boolean requireAudio) {
        android.media.MediaExtractor extractor = new android.media.MediaExtractor();
        try {
            extractor.setDataSource(this, uri, null);
            boolean video = false;
            boolean audio = false;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                String mime = extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME);
                if (mime == null || (!mime.startsWith("video/") && !mime.startsWith("audio/"))) continue;
                extractor.selectTrack(i);
                extractor.seekTo(0, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                if (extractor.getSampleSize() > 0) {
                    video |= mime.startsWith("video/");
                    audio |= mime.startsWith("audio/");
                }
                extractor.unselectTrack(i);
                if (video && (!requireAudio || audio)) return true;
            }
        } catch (IOException | RuntimeException ignored) {
        } finally { extractor.release(); }
        return false;
    }
}
