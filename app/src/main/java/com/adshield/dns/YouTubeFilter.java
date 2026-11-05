package com.adshield.dns;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Dedicated filter and classifier for YouTube-related domains.
 * Safely blocks verified advertisement banners, telemetry, and promotional endpoints
 * while guaranteeing uninterrupted video streaming and playback.
 */
public final class YouTubeFilter {

    private static final Set<String> YOUTUBE_AD_DOMAINS = new HashSet<>();
    private static final Set<String> ESSENTIAL_YOUTUBE_DOMAINS = new HashSet<>();

    static {
        // Verified advertising and telemetry endpoints
        YOUTUBE_AD_DOMAINS.add("ad.youtube.com");
        YOUTUBE_AD_DOMAINS.add("ads.youtube.com");
        YOUTUBE_AD_DOMAINS.add("youtubeads.l.google.com");
        YOUTUBE_AD_DOMAINS.add("googleads.g.doubleclick.net");
        YOUTUBE_AD_DOMAINS.add("pubads.g.doubleclick.net");
        YOUTUBE_AD_DOMAINS.add("securepubads.g.doubleclick.net");
        YOUTUBE_AD_DOMAINS.add("static.doubleclick.net");
        YOUTUBE_AD_DOMAINS.add("s.youtube.com");
        YOUTUBE_AD_DOMAINS.add("video-stats.l.google.com");
        YOUTUBE_AD_DOMAINS.add("pagead2.googlesyndication.com");
        YOUTUBE_AD_DOMAINS.add("pagead.l.google.com");
        YOUTUBE_AD_DOMAINS.add("adservice.google.com");
        YOUTUBE_AD_DOMAINS.add("ade.googlesyndication.com");
        YOUTUBE_AD_DOMAINS.add("adclick.g.doubleclick.net");
        YOUTUBE_AD_DOMAINS.add("partnerad.l.google.com");
        YOUTUBE_AD_DOMAINS.add("stats.g.doubleclick.net");

        // Essential domains that must NEVER be blocked (to avoid breaking video playback or login)
        ESSENTIAL_YOUTUBE_DOMAINS.add("youtube.com");
        ESSENTIAL_YOUTUBE_DOMAINS.add("www.youtube.com");
        ESSENTIAL_YOUTUBE_DOMAINS.add("m.youtube.com");
        ESSENTIAL_YOUTUBE_DOMAINS.add("googlevideo.com");
        ESSENTIAL_YOUTUBE_DOMAINS.add("ytimg.com");
        ESSENTIAL_YOUTUBE_DOMAINS.add("i.ytimg.com");
        ESSENTIAL_YOUTUBE_DOMAINS.add("youtubei.googleapis.com");
    }

    private YouTubeFilter() {}

    public static boolean isYouTubeRelated(String domain) {
        if (domain == null) return false;
        String clean = domain.toLowerCase(Locale.US);
        return clean.contains("youtube") || clean.contains("googlevideo") || clean.contains("ytimg");
    }

    public static boolean isYouTubeAd(String domain) {
        if (domain == null) return false;
        String clean = domain.toLowerCase(Locale.US).trim();
        while (clean.endsWith(".")) {
            clean = clean.substring(0, clean.length() - 1);
        }

        // Never block essential playback infrastructure
        if (isEssentialYouTube(clean)) {
            return false;
        }

        if (YOUTUBE_AD_DOMAINS.contains(clean)) {
            return true;
        }

        for (String adDomain : YOUTUBE_AD_DOMAINS) {
            if (clean.endsWith("." + adDomain)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEssentialYouTube(String domain) {
        if (domain == null) return false;
        String clean = domain.toLowerCase(Locale.US).trim();

        if (ESSENTIAL_YOUTUBE_DOMAINS.contains(clean)) {
            return true;
        }

        // e.g. rr1---sn-xxx.googlevideo.com
        return clean.endsWith(".googlevideo.com") || clean.endsWith(".ytimg.com");
    }
}
