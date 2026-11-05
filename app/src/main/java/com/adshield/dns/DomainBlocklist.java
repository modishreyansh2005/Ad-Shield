package com.adshield.dns;

import com.adshield.data.PreferencesManager;
import com.adshield.data.db.AdShieldDbHelper;
import com.adshield.data.model.FilterRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class DomainBlocklist {

    public static final Set<String> BUILT_IN_BLOCKED_DOMAINS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // Required initial test domains
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.com",

            // Major mobile advertising & tracking platforms
            "adnxs.com",
            "advertising.com",
            "adcolony.com",
            "applovin.com",
            "unityads.unity3d.com",
            "vungle.com",
            "criteo.com",
            "taboola.com",
            "outbrain.com",
            "scorecardresearch.com",
            "quantserve.com",
            "amazon-adsystem.com",
            "popads.net",
            "admob.com",
            "mopub.com",
            "inmobi.com",
            "chartboost.com",
            "ironsrc.com",
            "adjust.com",
            "appsflyer.com",
            "appsflyersdk.com",
            "branch.io",

            // JioCinema & Hotstar pure ad networks
            "jioads.com",
            "vserve.mobi",
            "imasdk.googleapis.com"
    )));

    private final Set<String> defaultBlockedDomains = new HashSet<>(BUILT_IN_BLOCKED_DOMAINS);
    private final AdShieldDbHelper dbHelper;
    private final PreferencesManager preferencesManager;

    public DomainBlocklist(AdShieldDbHelper dbHelper, PreferencesManager preferencesManager) {
        this.dbHelper = dbHelper;
        this.preferencesManager = preferencesManager;
    }

    public DomainBlocklist() {
        this(null, null);
    }

    public Set<String> getDefaultBlockedDomains() {
        return BUILT_IN_BLOCKED_DOMAINS;
    }

    public boolean isBlocked(String domain) {
        if (domain == null) return false;
        String clean = normalize(domain);
        if (clean.isEmpty()) return false;

        // 1. Whitelist has top priority: if domain is whitelisted, NEVER block it
        if (isWhitelisted(clean)) {
            return false;
        }

        // 2. Custom Blacklist check
        if (isBlacklisted(clean)) {
            return true;
        }

        // 3. YouTube specific filtering check
        if (preferencesManager == null || preferencesManager.isYouTubeProtectionEnabled()) {
            if (YouTubeFilter.isYouTubeAd(clean)) {
                return true;
            }
        }

        // 4. Spotify specific filtering check
        if (preferencesManager == null || preferencesManager.isSpotifyProtectionEnabled()) {
            if (SpotifyFilter.isSpotifyAd(clean)) {
                return true;
            }
        }

        // 5. JioHotstar & JioCinema specific filtering check
        if (preferencesManager == null || preferencesManager.isHotstarProtectionEnabled()) {
            if (JioHotstarFilter.isHotstarAd(clean)) {
                return true;
            }
        }

        // 6. Default blocklist matching (hierarchical parent check)
        if (defaultBlockedDomains.contains(clean)) {
            return true;
        }

        int dotIndex = clean.indexOf('.');
        while (dotIndex != -1 && dotIndex < clean.length() - 1) {
            String parent = clean.substring(dotIndex + 1);
            if (defaultBlockedDomains.contains(parent)) {
                return true;
            }
            dotIndex = clean.indexOf('.', dotIndex + 1);
        }

        return false;
    }

    private boolean isWhitelisted(String cleanDomain) {
        if (dbHelper == null) return false;
        Set<String> whitelisted = dbHelper.getActiveRuleDomains(FilterRule.Type.WHITELIST);
        if (whitelisted.contains(cleanDomain)) return true;

        int dot = cleanDomain.indexOf('.');
        while (dot != -1 && dot < cleanDomain.length() - 1) {
            if (whitelisted.contains(cleanDomain.substring(dot + 1))) {
                return true;
            }
            dot = cleanDomain.indexOf('.', dot + 1);
        }
        return false;
    }

    private boolean isBlacklisted(String cleanDomain) {
        if (dbHelper == null) return false;
        Set<String> blacklisted = dbHelper.getActiveRuleDomains(FilterRule.Type.BLACKLIST);
        if (blacklisted.contains(cleanDomain)) return true;

        int dot = cleanDomain.indexOf('.');
        while (dot != -1 && dot < cleanDomain.length() - 1) {
            if (blacklisted.contains(cleanDomain.substring(dot + 1))) {
                return true;
            }
            dot = cleanDomain.indexOf('.', dot + 1);
        }
        return false;
    }

    public static String normalize(String domain) {
        if (domain == null) return "";
        String s = domain.trim().toLowerCase(Locale.US);
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
