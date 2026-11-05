package com.adshield.dns;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Dedicated filter and classifier for JioHotstar, Disney+ Hotstar, and JioCinema ad domains.
 * Blocks pure third-party advertising brokers, promo banners, and tracker networks
 * while strictly guaranteeing that Hotstar app launch, authentication, Bifrost gateway,
 * configuration, and live cricket/video streaming never fail.
 */
public final class JioHotstarFilter {

    private static final Set<String> HOTSTAR_AD_DOMAINS = new HashSet<>();
    private static final Set<String> ESSENTIAL_HOTSTAR_DOMAINS = new HashSet<>();

    static {
        // Pure standalone advertising networks and ad delivery endpoints
        HOTSTAR_AD_DOMAINS.add("ads.hotstar.com");
        HOTSTAR_AD_DOMAINS.add("ad.hotstar.com");
        HOTSTAR_AD_DOMAINS.add("adengine.hotstar.com");
        HOTSTAR_AD_DOMAINS.add("adtech.hotstar.com");
        HOTSTAR_AD_DOMAINS.add("adtracking.hotstar.com");
        HOTSTAR_AD_DOMAINS.add("in-ads.hotstar.com");
        HOTSTAR_AD_DOMAINS.add("click.hotstar.com");
        HOTSTAR_AD_DOMAINS.add("conv.hotstar.com");
        HOTSTAR_AD_DOMAINS.add("hotstar-ads.akamaized.net");
        HOTSTAR_AD_DOMAINS.add("hotstar.admaster.com.cn");
        HOTSTAR_AD_DOMAINS.add("cricket-ads.hotstar.com");
        HOTSTAR_AD_DOMAINS.add("live-ads.hotstar.com");
        HOTSTAR_AD_DOMAINS.add("gads.hotstar.com");
        HOTSTAR_AD_DOMAINS.add("h-ads.hotstar.com");

        // JioCinema & Jio Ads Network endpoints
        HOTSTAR_AD_DOMAINS.add("ads.jiocinema.com");
        HOTSTAR_AD_DOMAINS.add("ad.jiocinema.com");
        HOTSTAR_AD_DOMAINS.add("adengine.jiocinema.com");
        HOTSTAR_AD_DOMAINS.add("jioads.akamaized.net");
        HOTSTAR_AD_DOMAINS.add("adtech.jio.com");
        HOTSTAR_AD_DOMAINS.add("ads.jio.com");
        HOTSTAR_AD_DOMAINS.add("jioads.com");
        HOTSTAR_AD_DOMAINS.add("api.jioads.com");
        HOTSTAR_AD_DOMAINS.add("sdk.jioads.com");
        HOTSTAR_AD_DOMAINS.add("cdn.jioads.com");
        HOTSTAR_AD_DOMAINS.add("ads.voot.com");
        HOTSTAR_AD_DOMAINS.add("ad.voot.com");
        HOTSTAR_AD_DOMAINS.add("voot-ads.akamaized.net");
        HOTSTAR_AD_DOMAINS.add("vserve.mobi");
        HOTSTAR_AD_DOMAINS.add("inmobi.com");
        HOTSTAR_AD_DOMAINS.add("imasdk.googleapis.com");

        // Essential app launch, authentication, API, and playback infrastructure
        // These MUST NEVER be blocked, or the Hotstar app will fail to open or play video.
        ESSENTIAL_HOTSTAR_DOMAINS.add("hotstar.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("www.hotstar.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("api.hotstar.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("hses.hotstar.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("bifrost.hotstar.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("bifrost-api.hotstar.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("telemetry.hotstar.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("client-telemetry.hotstar.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("analytics.hotstar.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("logx.hotstar.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("config.hotstar.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("secure-dpt.hotstar.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("auth.hotstar.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("api.hotstar-cdn.net");
        ESSENTIAL_HOTSTAR_DOMAINS.add("api-jiostar.bitdrift.io");
        ESSENTIAL_HOTSTAR_DOMAINS.add("bitdrift.io");
        ESSENTIAL_HOTSTAR_DOMAINS.add("c.conviva.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("livepass.conviva.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("jiocinema.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("www.jiocinema.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("api.jiocinema.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("content.jiocinema.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("playback.jiocinema.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("voot.com");
        ESSENTIAL_HOTSTAR_DOMAINS.add("jio.com");
    }

    private JioHotstarFilter() {}

    public static boolean isHotstarRelated(String domain) {
        if (domain == null) return false;
        String clean = domain.toLowerCase(Locale.US);
        return clean.contains("hotstar") || clean.contains("jiocinema") || clean.contains("jioads");
    }

    public static boolean isHotstarAd(String domain) {
        if (domain == null) return false;
        String clean = domain.toLowerCase(Locale.US).trim();
        while (clean.endsWith(".")) {
            clean = clean.substring(0, clean.length() - 1);
        }

        // 1. Whitelist essential playback & app startup infrastructure
        if (isEssentialHotstar(clean)) {
            return false;
        }

        // 2. Match exact ad domains
        if (HOTSTAR_AD_DOMAINS.contains(clean)) {
            return true;
        }

        // 3. Match subdomains of ad networks
        for (String adDomain : HOTSTAR_AD_DOMAINS) {
            if (clean.endsWith("." + adDomain)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isEssentialHotstar(String domain) {
        if (domain == null) return false;
        String clean = domain.toLowerCase(Locale.US).trim();

        if (ESSENTIAL_HOTSTAR_DOMAINS.contains(clean)) {
            return true;
        }

        // Essential CDNs, video playback streams, and telemetry required for app startup
        if (clean.endsWith(".hotstar-cdn.net") ||
            clean.endsWith(".playback.jiocinema.com") ||
            clean.endsWith(".conviva.com") ||
            clean.endsWith(".bitdrift.io")) {
            return true;
        }

        return false;
    }
}
