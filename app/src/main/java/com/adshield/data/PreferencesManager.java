package com.adshield.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.HashSet;
import java.util.Set;

public class PreferencesManager {

    private static final String PREFS_NAME = "adshield_preferences";

    private static final String KEY_PROTECTION_ENABLED = "key_protection_enabled";
    private static final String KEY_START_ON_BOOT = "key_start_on_boot";
    private static final String KEY_BLOCK_MALICIOUS = "key_block_malicious";
    private static final String KEY_BLOCKED_COUNT = "key_blocked_count";
    private static final String KEY_TOTAL_QUERIES = "key_total_queries";
    private static final String KEY_EXCLUDED_PACKAGES = "key_excluded_packages";
    private static final String KEY_THEME_MODE = "key_theme_mode";
    private static final String KEY_DNS_PROVIDER = "key_dns_provider";
    private static final String KEY_CUSTOM_DNS = "key_custom_dns";
    private static final String KEY_DOH_ENABLED = "key_doh_enabled";
    private static final String KEY_YOUTUBE_PROTECTION = "key_youtube_protection";
    private static final String KEY_SPOTIFY_PROTECTION = "key_spotify_protection";
    private static final String KEY_HOTSTAR_PROTECTION = "key_hotstar_protection";
    private static final String KEY_YOUTUBE_BLOCKED_COUNT = "key_yt_blocked_count";
    private static final String KEY_SPOTIFY_BLOCKED_COUNT = "key_sp_blocked_count";
    private static final String KEY_HOTSTAR_BLOCKED_COUNT = "key_hotstar_blocked_count";
    private static final String KEY_AUTO_SKIPPER_ENABLED = "key_auto_skipper_enabled";
    private static final String KEY_AUTO_MUTE_ADS_ENABLED = "key_auto_mute_ads_enabled";
    private static final String KEY_AUTO_SKIPPED_ADS_COUNT = "key_auto_skipped_ads_count";
    private static final String KEY_AD_BLOCKER_ENABLED = "key_ad_blocker_enabled";
    private static final String KEY_TRACKER_SHIELD_ENABLED = "key_tracker_shield_enabled";

    private final SharedPreferences prefs;

    public PreferencesManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isProtectionEnabled() {
        return prefs.getBoolean(KEY_PROTECTION_ENABLED, false);
    }

    public void setProtectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, enabled).apply();
    }

    public boolean isStartOnBoot() {
        return prefs.getBoolean(KEY_START_ON_BOOT, false);
    }

    public void setStartOnBoot(boolean start) {
        prefs.edit().putBoolean(KEY_START_ON_BOOT, start).apply();
    }

    public boolean isBlockMaliciousDomains() {
        return prefs.getBoolean(KEY_BLOCK_MALICIOUS, true);
    }

    public void setBlockMaliciousDomains(boolean block) {
        prefs.edit().putBoolean(KEY_BLOCK_MALICIOUS, block).apply();
    }

    public boolean isAdBlockerEnabled() {
        return prefs.getBoolean(KEY_AD_BLOCKER_ENABLED, true);
    }

    public void setAdBlockerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AD_BLOCKER_ENABLED, enabled).apply();
    }

    public boolean isTrackerShieldEnabled() {
        return prefs.getBoolean(KEY_TRACKER_SHIELD_ENABLED, true);
    }

    public void setTrackerShieldEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRACKER_SHIELD_ENABLED, enabled).apply();
    }

    public long getBlockedRequestsCount() {
        return prefs.getLong(KEY_BLOCKED_COUNT, 0L);
    }

    public void setBlockedRequestsCount(long count) {
        prefs.edit().putLong(KEY_BLOCKED_COUNT, count).apply();
    }

    public long getTotalQueriesCount() {
        return prefs.getLong(KEY_TOTAL_QUERIES, 0L);
    }

    public void setTotalQueriesCount(long count) {
        prefs.edit().putLong(KEY_TOTAL_QUERIES, count).apply();
    }

    public Set<String> getExcludedPackages() {
        return new HashSet<>(prefs.getStringSet(KEY_EXCLUDED_PACKAGES, new HashSet<>()));
    }

    public void setExcludedPackages(Set<String> packages) {
        prefs.edit().putStringSet(KEY_EXCLUDED_PACKAGES, new HashSet<>(packages)).apply();
    }

    public void setPackageExcluded(String packageName, boolean excluded) {
        Set<String> current = getExcludedPackages();
        if (excluded) {
            current.add(packageName);
        } else {
            current.remove(packageName);
        }
        setExcludedPackages(current);
    }

    public int getThemeMode() {
        return prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    public String getDnsProvider() {
        return prefs.getString(KEY_DNS_PROVIDER, "cloudflare");
    }

    public void setDnsProvider(String provider) {
        prefs.edit().putString(KEY_DNS_PROVIDER, provider).apply();
    }

    public String getCustomDns() {
        return prefs.getString(KEY_CUSTOM_DNS, "1.1.1.1");
    }

    public void setCustomDns(String customDns) {
        prefs.edit().putString(KEY_CUSTOM_DNS, customDns).apply();
    }

    public boolean isDohEnabled() {
        return prefs.getBoolean(KEY_DOH_ENABLED, false);
    }

    public void setDohEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DOH_ENABLED, enabled).apply();
    }

    public boolean isYouTubeProtectionEnabled() {
        return prefs.getBoolean(KEY_YOUTUBE_PROTECTION, true);
    }

    public void setYouTubeProtectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_YOUTUBE_PROTECTION, enabled).apply();
    }

    public boolean isSpotifyProtectionEnabled() {
        return prefs.getBoolean(KEY_SPOTIFY_PROTECTION, true);
    }

    public void setSpotifyProtectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SPOTIFY_PROTECTION, enabled).apply();
    }

    public long getYouTubeBlockedCount() {
        return prefs.getLong(KEY_YOUTUBE_BLOCKED_COUNT, 0L);
    }

    public void incrementYouTubeBlockedCount() {
        long current = getYouTubeBlockedCount();
        prefs.edit().putLong(KEY_YOUTUBE_BLOCKED_COUNT, current + 1).apply();
    }

    public long getSpotifyBlockedCount() {
        return prefs.getLong(KEY_SPOTIFY_BLOCKED_COUNT, 0L);
    }

    public void incrementSpotifyBlockedCount() {
        long current = getSpotifyBlockedCount();
        prefs.edit().putLong(KEY_SPOTIFY_BLOCKED_COUNT, current + 1).apply();
    }

    public boolean isHotstarProtectionEnabled() {
        return prefs.getBoolean(KEY_HOTSTAR_PROTECTION, true);
    }

    public void setHotstarProtectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HOTSTAR_PROTECTION, enabled).apply();
    }

    public long getHotstarBlockedCount() {
        return prefs.getLong(KEY_HOTSTAR_BLOCKED_COUNT, 0L);
    }

    public void incrementHotstarBlockedCount() {
        long current = getHotstarBlockedCount();
        prefs.edit().putLong(KEY_HOTSTAR_BLOCKED_COUNT, current + 1).apply();
    }

    public boolean isAutoSkipperEnabled() {
        return prefs.getBoolean(KEY_AUTO_SKIPPER_ENABLED, true);
    }

    public void setAutoSkipperEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_SKIPPER_ENABLED, enabled).apply();
    }

    public boolean isAutoMuteAdsEnabled() {
        return prefs.getBoolean(KEY_AUTO_MUTE_ADS_ENABLED, true);
    }

    public void setAutoMuteAdsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_MUTE_ADS_ENABLED, enabled).apply();
    }

    public long getAutoSkippedAdsCount() {
        return prefs.getLong(KEY_AUTO_SKIPPED_ADS_COUNT, 0L);
    }

    public void incrementAutoSkippedAdsCount() {
        long current = getAutoSkippedAdsCount();
        prefs.edit().putLong(KEY_AUTO_SKIPPED_ADS_COUNT, current + 1).apply();
    }

    public static boolean isAccessibilityServiceRunning(Context context) {
        if (context == null) return false;
        String expectedService = context.getPackageName() + "/com.adshield.service.AdShieldAccessibilityService";
        String enabledServices = android.provider.Settings.Secure.getString(
                context.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) return false;
        android.text.TextUtils.SimpleStringSplitter colonSplitter = new android.text.TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServices);
        while (colonSplitter.hasNext()) {
            String componentNameString = colonSplitter.next();
            if (expectedService.equalsIgnoreCase(componentNameString)) {
                return true;
            }
        }
        return false;
    }

    public void resetStatistics() {
        prefs.edit()
                .putLong(KEY_BLOCKED_COUNT, 0L)
                .putLong(KEY_TOTAL_QUERIES, 0L)
                .putLong(KEY_YOUTUBE_BLOCKED_COUNT, 0L)
                .putLong(KEY_SPOTIFY_BLOCKED_COUNT, 0L)
                .putLong(KEY_HOTSTAR_BLOCKED_COUNT, 0L)
                .putLong(KEY_AUTO_SKIPPED_ADS_COUNT, 0L)
                .apply();
    }
}
