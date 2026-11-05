package com.adshield.dns;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Dedicated filter and classifier for Spotify-related domains.
 * Blocks verified audio ad servers and tracking telemetry
 * while guaranteeing seamless music playback, login, and search.
 */
public final class SpotifyFilter {

    private static final Set<String> SPOTIFY_AD_DOMAINS = new HashSet<>();
    private static final Set<String> ESSENTIAL_SPOTIFY_DOMAINS = new HashSet<>();

    static {
        // Verified Spotify ad, tracking, and telemetry domains
        SPOTIFY_AD_DOMAINS.add("aet.spotify.com");
        SPOTIFY_AD_DOMAINS.add("adeventtracker.spotify.com");
        SPOTIFY_AD_DOMAINS.add("ads-fa.spotify.com");
        SPOTIFY_AD_DOMAINS.add("crashdump.spotify.com");
        SPOTIFY_AD_DOMAINS.add("adstudio.spotify.com");
        SPOTIFY_AD_DOMAINS.add("video-fa.scdn.co");
        SPOTIFY_AD_DOMAINS.add("gated.spotify.com");

        // Essential domains that must NEVER be blocked (to avoid breaking music streaming)
        ESSENTIAL_SPOTIFY_DOMAINS.add("spotify.com");
        ESSENTIAL_SPOTIFY_DOMAINS.add("www.spotify.com");
        ESSENTIAL_SPOTIFY_DOMAINS.add("api.spotify.com");
        ESSENTIAL_SPOTIFY_DOMAINS.add("spclient.wg.spotify.com");
        ESSENTIAL_SPOTIFY_DOMAINS.add("dealer.spotify.com");
        ESSENTIAL_SPOTIFY_DOMAINS.add("ap.spotify.com");
        ESSENTIAL_SPOTIFY_DOMAINS.add("login5.spotify.com");
        ESSENTIAL_SPOTIFY_DOMAINS.add("i.scdn.co");
        ESSENTIAL_SPOTIFY_DOMAINS.add("audio-fa.spotify.com");
        ESSENTIAL_SPOTIFY_DOMAINS.add("audio-ak.spotify.com");
        ESSENTIAL_SPOTIFY_DOMAINS.add("audio4-ak-spotify-com.akamaized.net");
    }

    private SpotifyFilter() {}

    public static boolean isSpotifyRelated(String domain) {
        if (domain == null) return false;
        String clean = domain.toLowerCase(Locale.US);
        return clean.contains("spotify") || clean.contains("scdn.co") || clean.contains("pscdn.co");
    }

    public static boolean isSpotifyAd(String domain) {
        if (domain == null) return false;
        String clean = domain.toLowerCase(Locale.US).trim();
        while (clean.endsWith(".")) {
            clean = clean.substring(0, clean.length() - 1);
        }

        // Never block essential audio streaming infrastructure
        if (isEssentialSpotify(clean)) {
            return false;
        }

        if (SPOTIFY_AD_DOMAINS.contains(clean)) {
            return true;
        }

        for (String adDomain : SPOTIFY_AD_DOMAINS) {
            if (clean.endsWith("." + adDomain)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEssentialSpotify(String domain) {
        if (domain == null) return false;
        String clean = domain.toLowerCase(Locale.US).trim();

        if (ESSENTIAL_SPOTIFY_DOMAINS.contains(clean)) {
            return true;
        }

        return clean.endsWith(".pscdn.co") || clean.endsWith(".scdn.co") || clean.endsWith(".akamaized.net");
    }
}
