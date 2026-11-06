package com.adshield.service;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import com.adshield.data.PreferencesManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutes ads in the official YouTube, YT Music and Spotify apps by watching their media sessions
 * (now-playing title/artist). Uses the user-granted "Notification access" permission, which is
 * what MediaSessionManager requires to see other apps' sessions. Does not read notification content.
 */
public class MediaAdMuteService extends NotificationListenerService {

    private static final String TAG = "MediaAdMute";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<MediaSession.Token, TrackedSession> tracked = new HashMap<>();
    private MediaSessionManager sessionManager;
    private PreferencesManager prefs;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = this::syncSessions;

    private final class TrackedSession extends MediaController.Callback {
        final MediaController controller;
        final String muteReason;
        private String lastLogLine = null;

        TrackedSession(MediaController controller) {
            this.controller = controller;
            this.muteReason = "session:" + controller.getPackageName();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            evaluate();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            evaluate();
        }

        @Override
        public void onSessionDestroyed() {
            AdMuteController.setMuted(MediaAdMuteService.this, muteReason, false);
        }

        void evaluate() {
            String pkg = controller.getPackageName();
            MediaMetadata md = controller.getMetadata();
            PlaybackState state = controller.getPlaybackState();
            boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;

            String title = null, artist = null, mediaId = null;
            long durationMs = 0;
            if (md != null) {
                title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                mediaId = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
                durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            }

            boolean enabled = prefs.isAutoMuteAdsEnabled()
                    && (!MediaAdDetector.PKG_SPOTIFY.equals(pkg) || prefs.isSpotifyProtectionEnabled())
                    && (MediaAdDetector.PKG_SPOTIFY.equals(pkg) || prefs.isYouTubeProtectionEnabled());
            long actions = (state != null) ? state.getActions() : -1;
            boolean ad = enabled && playing && MediaAdDetector.isAd(pkg, title, artist, mediaId, durationMs, actions);

            // Logged (only on change) so ad rules can be tuned from real devices: adb logcat -s MediaAdMute
            String logLine = "[METADATA] pkg=" + pkg + " playing=" + playing + " title=" + title
                    + " artist=" + artist + " id=" + mediaId + " durationMs=" + durationMs
                    + " actions=" + actions + " ad=" + ad;
            if (!logLine.equals(lastLogLine)) {
                Log.d(TAG, logLine);
                lastLogLine = logLine;
            }

            AdMuteController.setMuted(MediaAdMuteService.this, muteReason, ad);
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        prefs = new PreferencesManager(this);
        sessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        ComponentName self = new ComponentName(this, MediaAdMuteService.class);
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, self, mainHandler);
            syncSessions(sessionManager.getActiveSessions(self));
        } catch (SecurityException e) {
            Log.w(TAG, "Notification access not granted: " + e.getMessage());
        }
        // This listener stays alive while access is granted, so it also hosts the Spotify broadcast muter
        SpotifyAdMuter.register(this);
        Log.i(TAG, "Media ad mute listener connected");
    }

    @Override
    public void onListenerDisconnected() {
        if (sessionManager != null) {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener);
        }
        clearTracked();
        SpotifyAdMuter.unregister();
        Log.i(TAG, "Media ad mute listener disconnected");
        super.onListenerDisconnected();
    }

    private void syncSessions(List<MediaController> controllers) {
        Map<MediaSession.Token, TrackedSession> stillActive = new HashMap<>();
        if (controllers != null) {
            for (MediaController controller : controllers) {
                if (!MediaAdDetector.TARGET_PACKAGES.contains(controller.getPackageName())) continue;

                MediaSession.Token token = controller.getSessionToken();
                TrackedSession session = tracked.remove(token);
                if (session == null) {
                    session = new TrackedSession(controller);
                    controller.registerCallback(session, mainHandler);
                }
                stillActive.put(token, session);
            }
        }
        // Release ended sessions before evaluating live ones, so a stale unmute can't win
        clearTracked();
        tracked.putAll(stillActive);
        for (TrackedSession session : tracked.values()) {
            session.evaluate();
        }
    }

    /** Unregisters and unmutes every session still in {@link #tracked}. */
    private void clearTracked() {
        for (TrackedSession session : tracked.values()) {
            session.controller.unregisterCallback(session);
            AdMuteController.setMuted(this, session.muteReason, false);
        }
        tracked.clear();
    }

    public static boolean isAccessGranted(Context context) {
        String enabled = android.provider.Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName self = new ComponentName(context, MediaAdMuteService.class);
        for (String entry : enabled.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(entry);
            if (self.equals(cn)) return true;
        }
        return false;
    }
}
