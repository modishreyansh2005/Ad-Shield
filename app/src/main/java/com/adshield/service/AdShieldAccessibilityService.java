package com.adshield.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.adshield.data.PreferencesManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * AdShield Smart Accessibility Service.
 * Runs in the background and applies Brave Browser ad-defusing logic outside of AdShield:
 * 1. Automatically detects video ads in supported streaming apps.
 * 2. Clicks "Skip Ad" the exact millisecond it becomes available.
 * 3. Instantly mutes the media audio stream during unskippable ad segments.
 * 4. Dismisses promotional banner overlays.
 * 5. Restores original media audio when regular video resumes.
 */
public class AdShieldAccessibilityService extends AccessibilityService {

    private static final String TAG = "AdShieldAccessibility";

    private static final Set<String> TARGET_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            "com.google.android.youtube.tv",
            "com.spotify.music"
    ));

    private static final String[] YOUTUBE_SKIP_IDS = {
            "com.google.android.youtube:id/skip_ad_button",
            "com.google.android.youtube:id/modern_skip_ad_button",
            "com.google.android.youtube:id/skip_ad_button_text",
            "com.google.android.youtube:id/ad_skip_button"
    };

    private static final String[] OVERLAY_CLOSE_IDS = {
            "com.google.android.youtube:id/close_button",
            "com.google.android.youtube:id/action_sheet_close",
            "com.google.android.youtube:id/dismiss_button"
    };

    private PreferencesManager prefsManager;
    private AudioManager audioManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isMutedByAdShield = false;
    private int savedVolume = -1;
    private long lastSkipTimestamp = 0L;

    @Override
    public void onCreate() {
        super.onCreate();
        prefsManager = new PreferencesManager(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        Log.i(TAG, "AdShield Accessibility Service created");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String packageName = pkg.toString();

        if (!TARGET_PACKAGES.contains(packageName)) {
            // Not a targeted streaming app
            return;
        }

        if (!prefsManager.isAutoSkipperEnabled()) {
            // User disabled auto-skipper
            restoreAudioIfNeeded();
            return;
        }

        AccessibilityNodeInfo rootNode = null;
        try {
            rootNode = getRootInActiveWindow();
            if (rootNode == null) return;

            boolean adDetected = false;

            // 1. Check for Skip Ad Buttons by View ID
            boolean skipped = checkAndClickByIds(rootNode, YOUTUBE_SKIP_IDS);

            // 2. Check for Skip Ad Buttons by Text
            if (!skipped) {
                skipped = checkAndClickByText(rootNode, "Skip Ad");
            }
            if (!skipped) {
                skipped = checkAndClickByText(rootNode, "Skip ad");
            }
            if (!skipped) {
                skipped = checkAndClickByText(rootNode, "Skip Ads");
            }

            if (skipped) {
                adDetected = true;
                onAdSkippedSuccessfully();
            } else {
                // 3. Dismiss Banner Overlays if any
                checkAndClickByIds(rootNode, OVERLAY_CLOSE_IDS);

                // 4. Scan if an ad is actively playing (for auto-muting)
                adDetected = scanForActiveAd(rootNode, packageName);
            }

            // Handle Audio Muting (Brave logic)
            handleAudioMuting(adDetected);

        } catch (Exception e) {
            Log.e(TAG, "Error processing accessibility event", e);
        } finally {
            if (rootNode != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                try {
                    rootNode.recycle();
                } catch (Exception ignored) {}
            }
        }
    }

    private boolean checkAndClickByIds(AccessibilityNodeInfo root, String[] ids) {
        if (root == null || ids == null) return false;

        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node != null && performClick(node)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean checkAndClickByText(AccessibilityNodeInfo root, String text) {
        if (root == null || text == null) return false;

        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node != null) {
                    CharSequence nodeText = node.getText();
                    if (nodeText != null && nodeText.toString().toLowerCase(Locale.US).contains(text.toLowerCase(Locale.US))) {
                        if (performClick(node)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean performClick(AccessibilityNodeInfo targetNode) {
        if (targetNode == null) return false;

        long now = System.currentTimeMillis();
        if (now - lastSkipTimestamp < 500L) {
            // Debounce within 500ms
            return false;
        }

        AccessibilityNodeInfo current = targetNode;
        int depth = 0;
        while (current != null && !current.isClickable() && depth < 5) {
            current = current.getParent();
            depth++;
        }

        if (current != null && current.isClickable()) {
            boolean success = current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (success) {
                lastSkipTimestamp = now;
                Log.d(TAG, "Successfully triggered Skip Ad click!");
                return true;
            }
        }
        return false;
    }

    private boolean scanForActiveAd(AccessibilityNodeInfo root, String packageName) {
        if (root == null) return false;

        // Check common ad text markers
        String[] adTexts = {"Ad ·", "Ad 1 of", "Ad 2 of", "Sponsored", "Visit advertiser", "Ad will end in", "Skip in "};
        for (String txt : adTexts) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(txt);
            if (nodes != null && !nodes.isEmpty()) {
                return true;
            }
        }

        // Check ad countdown view IDs
        String[] adViewIds = {
                "com.google.android.youtube:id/ad_progress_text",
                "com.google.android.youtube:id/ad_countdown",
                "com.google.android.youtube:id/ad_headline"
        };
        for (String viewId : adViewIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
            if (nodes != null && !nodes.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private void handleAudioMuting(boolean isAdActive) {
        if (!prefsManager.isAutoMuteAdsEnabled() || audioManager == null) {
            restoreAudioIfNeeded();
            return;
        }

        if (isAdActive) {
            if (!isMutedByAdShield) {
                try {
                    int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    if (currentVol > 0) {
                        savedVolume = currentVol;
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                        isMutedByAdShield = true;
                        Log.d(TAG, "Ad detected: muted media audio (saved: " + savedVolume + ")");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error muting media stream", e);
                }
            }
        } else {
            restoreAudioIfNeeded();
        }
    }

    private void restoreAudioIfNeeded() {
        if (isMutedByAdShield && audioManager != null) {
            try {
                if (savedVolume > 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0);
                    Log.d(TAG, "Ad ended: restored media audio to " + savedVolume);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error restoring media volume", e);
            } finally {
                isMutedByAdShield = false;
                savedVolume = -1;
            }
        }
    }

    private void onAdSkippedSuccessfully() {
        prefsManager.incrementAutoSkippedAdsCount();
        restoreAudioIfNeeded();
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "AdShield Accessibility Service interrupted");
        restoreAudioIfNeeded();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        restoreAudioIfNeeded();
        Log.i(TAG, "AdShield Accessibility Service destroyed");
    }
}
