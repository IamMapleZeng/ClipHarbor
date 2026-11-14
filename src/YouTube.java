package local.codex.xsavevideo;

import android.content.Context;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class YouTube {
    static final class DownloadError extends IOException {
        DownloadError(String message) {
            super(message);
        }
    }
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern PROGRESS = Pattern.compile("^clipharbor:\\s*([0-9.]+)%");
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com"));
    private static final Object RUNTIME_LOCK = new Object();
    private final Context context;
    private volatile Process active;

    YouTube(Context context) {
        this.context = context;
    }

    static String videoId(URI link) {
        if (link.getHost() == null) return null;
        String host = link.getHost().toLowerCase(Locale.ROOT);
        String path = link.getPath();
        if ("youtu.be".equals(host)) {
            return valid(path.replaceFirst("^/", "").replaceFirst("/$", ""));
        }
        if (!HOSTS.contains(host)) return null;
        if ("/watch".equals(path) && link.getRawQuery() != null) {
            for (String parameter : link.getRawQuery().split("&")) {
                if (parameter.startsWith("v=")) return valid(parameter.substring(2));
            }
        }
        for (String prefix : Arrays.asList("/shorts/", "/live/", "/embed/")) {
            if (path.startsWith(prefix)) return valid(path.substring(prefix.length()).replaceFirst("/$", ""));
        }
        return null;
    }

    private static String valid(String id) {
        return ID.matcher(id).matches() ? id : null;
    }

    void cancel() {
        Process child = active;
        if (child != null) child.destroy();
    }

    void download(String id, File output, BooleanSupplier cancelled, IntConsumer progress)
            throws IOException, InterruptedException {
        File nativeDir = new File(context.getApplicationInfo().nativeLibraryDir);
        File python = new File(nativeDir, "libpython.so");
        if (!python.canExecute()) throw new DownloadError("YouTube 下载组件无法启动。");
        File runtime = prepareRuntime(cancelled);
        checkCancelled(cancelled);
        File usr = new File(runtime, "usr");
        ProcessBuilder command = new ProcessBuilder(python.getPath(), "-m", "yt_dlp",
            "--ignore-config", "--no-playlist", "--no-cache-dir", "--no-mtime",
            "--no-remote-components", "--js-runtimes", "quickjs:" + new File(nativeDir, "libqjs.so"),
            "--socket-timeout", "12", "--retries", "1", "--extractor-retries", "1",
            "--no-simulate", "--match-filter", "!is_live & !is_upcoming",
            "--format", "b[ext=mp4][protocol=https][vcodec^=avc1][acodec^=mp4a]",
            "--newline", "--progress-template", "download:clipharbor:%(progress._percent_str)s",
            "--output", output.getPath(), "https://www.youtube.com/watch?v=" + id);
        command.redirectErrorStream(true);
        command.directory(output.getParentFile());
        Map<String, String> env = command.environment();
        env.put("HOME", output.getParent());
        env.put("TMPDIR", output.getParent());
        env.put("PYTHONHOME", usr.getPath());
        env.put("PYTHONDONTWRITEBYTECODE", "1");
        env.put("LD_LIBRARY_PATH", new File(usr, "lib").getPath());
        env.put("SSL_CERT_FILE", new File(usr, "etc/tls/cert.pem").getPath());
        File wheels = new File(runtime, "wheels");
        File[] packages = wheels.listFiles();
        if (packages == null || packages.length != 2) throw new IOException("YouTube 下载组件不完整。");
        StringJoiner pythonPath = new StringJoiner(File.pathSeparator);
        for (File wheel : packages) pythonPath.add(wheel.getPath());
        env.put("PYTHONPATH", pythonPath.toString());
        Process child = command.start();
        active = child;
        StringBuilder errors = new StringBuilder();
        try {
            checkCancelled(cancelled);
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(child.getInputStream()))) {
                int previous = -1;
                for (String line; (line = lines.readLine()) != null;) {
                    checkCancelled(cancelled);
                    Matcher match = PROGRESS.matcher(line);
                    if (match.find()) {
                        int percent = Math.min(100, (int) Double.parseDouble(match.group(1)));
                        if (percent != previous) progress.accept(percent);
                        previous = percent;
                    } else if (line.startsWith("ERROR:") && errors.length() < 2000) {
                        errors.append(line).append('\n');
                    }
                }
            }
            if (child.waitFor() != 0 || !output.isFile() || output.length() == 0) {
                throw new DownloadError(errorMessage(errors.toString()));
            }
        } finally {
            child.destroy();
            active = null;
        }
    }

    private static String errorMessage(String message) {
        String reason = message.toLowerCase(Locale.ROOT);
        if (reason.contains("not a bot") || reason.contains("sign in")) {
            return "YouTube 要求验证访问，当前网络无法下载这条视频。可换个网络后重试。";
        }
        if (reason.contains("403") || reason.contains("429")) {
            return "YouTube 暂时拒绝下载。请稍后重试。";
        }
        if (reason.contains("no space")) return "手机存储空间不足。";
        return "未能取得带声音的 MP4。请检查网络，或试一条其他的公开视频。";
    }

    private File prepareRuntime(BooleanSupplier cancelled) throws IOException {
        synchronized (RUNTIME_LOCK) {
            File directory = new File(context.getFilesDir(), "youtube-2026.10.26");
            File ready = new File(directory, ".ready");
            if (ready.isFile()) return directory;
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法准备下载组件。");
            String prefix = directory.getCanonicalPath() + File.separator;
            try (ZipInputStream zip = new ZipInputStream(context.getAssets().open("youtube-runtime.zip"))) {
                byte[] buffer = new byte[65536];
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                    checkCancelled(cancelled);
                    if (entry.isDirectory()) continue;
                    File target = new File(directory, entry.getName());
                    if (!target.getCanonicalPath().startsWith(prefix)) throw new IOException("下载组件文件无效。");
                    File parent = target.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法准备下载组件。");
                    try (OutputStream out = new FileOutputStream(target)) {
                        for (int size; (size = zip.read(buffer)) != -1;) {
                            checkCancelled(cancelled);
                            out.write(buffer, 0, size);
                        }
                    }
                }
            }
            if (!ready.createNewFile()) throw new IOException("无法初始化下载组件。");
            return directory;
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedIOException {
        if (cancelled.getAsBoolean()) throw new InterruptedIOException("Cancelled");
    }
}
