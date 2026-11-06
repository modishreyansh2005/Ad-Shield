package com.adshield.service;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Single owner of AdShield's media mute. Several detectors (Spotify broadcasts, media sessions)
 * can each request a mute under their own reason; audio is unmuted only when no reason remains,
 * so one detector can't unmute an ad another detector is still muting.
 */
public final class AdMuteController {

    private static final String TAG = "AdMuteController";

    private static final Set<String> activeReasons = new HashSet<>();
    private static boolean mutedByUs = false;

    private AdMuteController() {}

    public static synchronized void setMuted(Context context, String reason, boolean muted) {
        boolean changed = muted ? activeReasons.add(reason) : activeReasons.remove(reason);
        if (!changed) return;

        AudioManager audioManager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;

        try {
            if (!activeReasons.isEmpty() && !mutedByUs) {
                // ADJUST_MUTE keeps the user's volume level, so unmuting restores it exactly
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                mutedByUs = true;
                Log.d(TAG, "Muted media audio (" + reason + ")");
            } else if (activeReasons.isEmpty() && mutedByUs) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
                mutedByUs = false;
                Log.d(TAG, "Restored media audio");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error changing media mute", e);
        }
    }
}
