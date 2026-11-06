package com.adshield.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.adshield.data.PreferencesManager;

import java.util.Locale;

/**
 * Mutes Spotify audio ads even when Spotify is in the background or the screen is off.
 * Relies on Spotify's media broadcasts, which the user must enable in
 * Spotify → Settings → "Device Broadcast Status". Implicit broadcasts can't reach
 * manifest receivers on Android 8+, so this is registered at runtime by the long-lived
 * VPN service and the notification-access listener (reference counted).
 */
public final class SpotifyAdMuter {

    private static final String TAG = "SpotifyAdMuter";

    static final String ACTION_METADATA_CHANGED = "com.spotify.music.metadatachanged";
    static final String ACTION_PLAYBACK_STATE_CHANGED = "com.spotify.music.playbackstatechanged";

    private static final long DEFAULT_AD_SAFETY_MS = 45_000L;
    private static final long MAX_AD_SAFETY_MS = 120_000L;
    private static final long SAFETY_PADDING_MS = 3_000L;
    private static final String MUTE_REASON = "spotify-broadcast";

    private static SpotifyAdMuter instance;
    private static int registrations = 0;

    private final Context appContext;
    private final PreferencesManager prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Never leave the phone muted if Spotify stops broadcasting mid-ad
    private final Runnable safetyUnmute = this::unmute;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;

            if (!prefs.isAutoMuteAdsEnabled() || !prefs.isSpotifyProtectionEnabled()) {
                unmute();
                return;
            }

            switch (intent.getAction()) {
                case ACTION_METADATA_CHANGED: {
                    String id = intent.getStringExtra("id");
                    String artist = intent.getStringExtra("artist");
                    String track = intent.getStringExtra("track");
                    int length = intent.getIntExtra("length", 0);
                    if (isAdTrack(id, artist, track)) {
                        Log.d(TAG, "Spotify ad detected: " + id);
                        mute(adSafetyDelayMs(length));
                    } else {
                        unmute();
                    }
                    break;
                }
                case ACTION_PLAYBACK_STATE_CHANGED:
                    if (!intent.getBooleanExtra("playing", true)) {
                        // Paused or stopped: give the user their sound back
                        unmute();
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private SpotifyAdMuter(Context context) {
        appContext = context.getApplicationContext();
        prefs = new PreferencesManager(appContext);
    }

    public static synchronized void register(Context context) {
        if (instance == null) {
            instance = new SpotifyAdMuter(context);
        }
        if (registrations++ == 0) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_METADATA_CHANGED);
            filter.addAction(ACTION_PLAYBACK_STATE_CHANGED);
            ContextCompat.registerReceiver(instance.appContext, instance.receiver, filter,
                    ContextCompat.RECEIVER_EXPORTED);
            Log.i(TAG, "Listening for Spotify broadcasts");
        }
    }

    public static synchronized void unregister() {
        if (instance == null || registrations == 0) return;
        if (--registrations == 0) {
            try {
                instance.appContext.unregisterReceiver(instance.receiver);
            } catch (IllegalArgumentException ignored) {}
            instance.unmute();
            Log.i(TAG, "Stopped listening for Spotify broadcasts");
        }
    }

    /**
     * Heuristic ad check on Spotify's broadcast metadata. Ads carry a "spotify:ad:" URI;
     * some app versions instead send an "Advertisement" title or blank track info.
     */
    static boolean isAdTrack(String id, String artist, String track) {
        String cleanId = (id == null) ? "" : id.trim().toLowerCase(Locale.US);
        String cleanArtist = (artist == null) ? "" : artist.trim().toLowerCase(Locale.US);
        String cleanTrack = (track == null) ? "" : track.trim().toLowerCase(Locale.US);

        if (cleanId.startsWith("spotify:ad:")) return true;
        if (cleanTrack.equals("advertisement") || cleanArtist.equals("advertisement")) return true;
        return cleanId.isEmpty() && cleanArtist.isEmpty() && cleanTrack.isEmpty();
    }

    /** Spotify's "length" extra is seconds on most versions, milliseconds on some. */
    static long adSafetyDelayMs(int length) {
        if (length <= 0) return DEFAULT_AD_SAFETY_MS;
        long ms = (length > 10_000) ? length : length * 1000L;
        return Math.min(ms, MAX_AD_SAFETY_MS) + SAFETY_PADDING_MS;
    }

    private void mute(long safetyDelayMs) {
        mainHandler.removeCallbacks(safetyUnmute);
        AdMuteController.setMuted(appContext, MUTE_REASON, true);
        mainHandler.postDelayed(safetyUnmute, safetyDelayMs);
    }

    private void unmute() {
        mainHandler.removeCallbacks(safetyUnmute);
        AdMuteController.setMuted(appContext, MUTE_REASON, false);
    }
}
