package com.adshield.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides from now-playing metadata (media session) whether an app is playing an ad.
 * Spotify rules are well known; YouTube / YT Music rules are best-effort and should be
 * tuned from the "[METADATA]" log lines MediaAdMuteService prints.
 */
public final class MediaAdDetector {

    public static final String PKG_YOUTUBE = "com.google.android.youtube";
    public static final String PKG_YT_MUSIC = "com.google.android.apps.youtube.music";
    public static final String PKG_SPOTIFY = "com.spotify.music";

    public static final Set<String> TARGET_PACKAGES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            PKG_YOUTUBE, PKG_YT_MUSIC, PKG_SPOTIFY
    )));

    private static final long SPOTIFY_MAX_AD_MS = 65_000L;

    // Same value as PlaybackState.ACTION_SEEK_TO; kept here so this class stays plain Java
    static final long ACTION_SEEK_TO = 1L << 8;

    private static final String[] YOUTUBE_AD_TITLE_MARKERS = {
            "video will play after ad", "your video will begin", "video will play after ads"
    };

    private MediaAdDetector() {}

    /**
     * @param actions the session's PlaybackState actions bitmask, or -1 if unknown
     */
    public static boolean isAd(String packageName, String title, String artist, String mediaId,
                               long durationMs, long actions) {
        String t = clean(title);
        String a = clean(artist);
        String id = clean(mediaId);

        // Generic ad labels any player may use
        if (t.equals("advertisement") || a.equals("advertisement") || t.equals("ad")
                || t.startsWith("ad ·") || t.startsWith("ad •")) {
            return true;
        }

        if (PKG_SPOTIFY.equals(packageName)) {
            if (id.startsWith("spotify:ad:")) return true;
            // Spotify labels audio ads with itself as the artist; real content is never that short
            return a.equals("spotify") && durationMs > 0 && durationMs <= SPOTIFY_MAX_AD_MS;
        }

        if (PKG_YOUTUBE.equals(packageName)) {
            // Verified on device: YouTube keeps the video's title during ads but disables seeking
            // (actions 8615 -> 8359). Callers only ask while playing, so loading states never match.
            if (actions >= 0 && (actions & ACTION_SEEK_TO) == 0) return true;
        }

        if (PKG_YOUTUBE.equals(packageName) || PKG_YT_MUSIC.equals(packageName)) {
            for (String marker : YOUTUBE_AD_TITLE_MARKERS) {
                if (t.contains(marker)) return true;
            }
        }
        return false;
    }

    private static String clean(String s) {
        return (s == null) ? "" : s.trim().toLowerCase(Locale.US);
    }
}
