package local.codex.xsavevideo;

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.*;

final class RedNote {
    static final String SITE = "https://www.xiaohongshu.com";
    static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
    private static final Pattern NOTE = Pattern.compile("/(?:explore|discovery/item)/([a-f0-9]{24})/?");
    private static final Pattern SHORT = Pattern.compile("/(?:(?:o|m)/)?[A-Za-z0-9_-]{4,40}/?");
    private static final Pattern STATE = Pattern.compile("window\\.__INITIAL_STATE__\\s*=\\s*");

    private RedNote() {}

    static String noteId(URI uri) {
        if (!isWeb(uri)) return null;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals("www.xiaohongshu.com") && !host.equals("xiaohongshu.com")
            && !host.equals("m.xiaohongshu.com")) return null;
        Matcher match = NOTE.matcher(uri.getPath());
        return match.matches() ? match.group(1) : null;
    }

    static boolean isShort(URI uri) {
        if (!isWeb(uri)) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return (host.equals("xhslink.cn") || host.equals("xhslink.com"))
            && SHORT.matcher(uri.getPath()).matches();
    }

    private static boolean isWeb(URI uri) {
        return uri.getHost() != null && uri.getUserInfo() == null && uri.getPort() == -1
            && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
    }

    static String canonicalNote(URI uri) {
        List<String> parameters = new ArrayList<>();
        if (uri.getRawQuery() != null) {
            for (String parameter : uri.getRawQuery().split("&")) {
                if (parameter.startsWith("xsec_token=") || parameter.startsWith("xsec_source=")) {
                    parameters.add(parameter);
                }
            }
        }
        return SITE + "/discovery/item/" + noteId(uri)
            + (parameters.isEmpty() ? "" : "?" + String.join("&", parameters));
    }

    static List<String> videoUrls(String page, String id) throws JSONException {
        Matcher marker = STATE.matcher(page);
        if (!marker.find()) return Collections.emptyList();
        JSONObject state = (JSONObject) new JSONTokener(page.substring(marker.end())).nextValue();
        JSONObject note = object(state, "noteData", "data", "noteData");
        if (note == null) note = object(state, "note", "noteDetailMap", id, "note");
        if (note == null || !id.equals(note.optString("noteId", id))) return Collections.emptyList();
        JSONObject stream = object(note, "video", "media", "stream");
        if (stream == null) return Collections.emptyList();

        Set<String> urls = new LinkedHashSet<>();
        for (String codec : Arrays.asList("h264", "h265")) {
            JSONArray tracks = stream.optJSONArray(codec);
            if (tracks == null) continue;
            List<JSONObject> ranked = new ArrayList<>();
            for (int i = 0; i < tracks.length(); i++) {
                JSONObject track = tracks.optJSONObject(i);
                if (track != null) ranked.add(track);
            }
            ranked.sort(Comparator.comparingLong((JSONObject track) -> track.optLong("avgBitrate")).reversed());
            for (JSONObject track : ranked) {
                addMedia(urls, track.optString("masterUrl"));
                JSONArray backups = track.optJSONArray("backupUrls");
                if (backups != null) for (int i = 0; i < backups.length(); i++) addMedia(urls, backups.optString(i));
            }
        }
        return new ArrayList<>(urls);
    }

    private static JSONObject object(JSONObject value, String... path) {
        for (String key : path) {
            if (value == null) return null;
            value = value.optJSONObject(key);
        }
        return value;
    }

    private static void addMedia(Set<String> urls, String value) {
        try {
            URI uri = URI.create(value);
            if (isWeb(uri) && uri.getHost().toLowerCase(Locale.ROOT).endsWith(".xhscdn.com")
                && uri.getPath() != null && !uri.getPath().isEmpty()) {
                urls.add("https" + value.substring(value.indexOf(':')));
            }
        } catch (IllegalArgumentException ignored) {}
    }
}
