package com.adshield.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.adshield.AdShieldApplication;
import com.adshield.MainActivity;
import com.adshield.R;
import com.adshield.data.PreferencesManager;
import com.adshield.data.db.AdShieldDbHelper;
import com.adshield.data.model.VpnState;
import com.adshield.dns.BlocklistStore;
import com.adshield.dns.CloudflareDnsForwarder;
import com.adshield.dns.DnsFilteringEngine;
import com.adshield.dns.DomainBlocklist;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdShieldVpnService extends VpnService {

    private static final String TAG = "AdShieldVpnService";
    public static final String ACTION_START = "com.adshield.action.START";
    public static final String ACTION_STOP = "com.adshield.action.STOP";
    public static final String ACTION_RECONNECT = "com.adshield.action.RECONNECT";

    private static final int NOTIFICATION_ID = 1001;
    private static final int DEFAULT_MTU = 1500;
    private static final String LOCAL_IP_ADDRESS = "10.0.0.2";
    private static final String LOCAL_DNS_SERVER = "10.0.0.1";
    private static final int LOCAL_PREFIX_LENGTH = 32;

    private static final MutableLiveData<VpnState> vpnStateLiveData = new MutableLiveData<>(VpnState.DISCONNECTED);
    private static final MutableLiveData<Long> blockedCountLiveData = new MutableLiveData<>(0L);
    private static final MutableLiveData<Long> totalQueriesLiveData = new MutableLiveData<>(0L);
    private static Long protectionStartTimeMillis = null;

    public static LiveData<VpnState> getVpnStateLiveData() {
        return vpnStateLiveData;
    }

    public static LiveData<Long> getBlockedCountLiveData() {
        return blockedCountLiveData;
    }

    public static LiveData<Long> getTotalQueriesLiveData() {
        return totalQueriesLiveData;
    }

    public static Long getProtectionStartTimeMillis() {
        return protectionStartTimeMillis;
    }

    private ParcelFileDescriptor vpnInterface = null;
    private volatile boolean isRunning = false;
    private Thread workerThread = null;
    private ExecutorService executor = null;
    private DnsFilteringEngine filteringEngine = null;

    @Override
    public void onCreate() {
        super.onCreate();
        SpotifyAdMuter.register(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent != null && intent.getAction() != null) ? intent.getAction() : ACTION_START;

        switch (action) {
            case ACTION_STOP:
                stopVpn();
                return START_NOT_STICKY;
            case ACTION_RECONNECT:
                // Rebuild the tunnel only; stopVpn() would also stopSelf() and kill the service
                teardownTunnel();
                startVpn();
                return START_STICKY;
            case ACTION_START:
            default:
                startVpn();
                return START_STICKY;
        }
    }

    private void startVpn() {
        if (isRunning) {
            // Already running (e.g. boot receiver + user tap): replace the old tunnel instead of leaking it
            teardownTunnel();
        }
        vpnStateLiveData.postValue(VpnState.CONNECTING);
        protectionStartTimeMillis = System.currentTimeMillis();

        try {
            Notification notification = buildForegroundNotification();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            PreferencesManager prefs = AdShieldApplication.getInstance().getPreferencesManager();
            AdShieldDbHelper dbHelper = AdShieldDbHelper.getInstance(this);

            Builder builder = new Builder();
            builder.setSession(getString(R.string.app_name));
            builder.setMtu(DEFAULT_MTU);
            builder.addAddress(LOCAL_IP_ADDRESS, LOCAL_PREFIX_LENGTH);
            builder.addDnsServer(LOCAL_DNS_SERVER);
            builder.addRoute(LOCAL_DNS_SERVER, LOCAL_PREFIX_LENGTH);

            // IPv6 Local Configuration (Essential for Indian cellular networks like Reliance Jio)
            try {
                builder.addAddress("fd00:1::2", 128);
                builder.addDnsServer("fd00:1::1");
                builder.addRoute("fd00:1::1", 128);
            } catch (Exception ignored) {}

            builder.setBlocking(true);

            // Per-app package exclusions (Disallowed apps bypass VPN tunnel completely)
            Set<String> excludedPackages = prefs.getExcludedPackages();
            PackageManager pm = getPackageManager();
            for (String pkg : excludedPackages) {
                try {
                    pm.getPackageInfo(pkg, 0);
                    builder.addDisallowedApplication(pkg);
                } catch (PackageManager.NameNotFoundException ignored) {}
            }

            PendingIntent mainPendingIntent = PendingIntent.getActivity(
                    this, 0,
                    new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.setConfigureIntent(mainPendingIntent);

            vpnInterface = builder.establish();

            if (vpnInterface != null) {
                prefs.setProtectionEnabled(true);
                blockedCountLiveData.postValue(prefs.getBlockedRequestsCount());
                totalQueriesLiveData.postValue(prefs.getTotalQueriesCount());
                vpnStateLiveData.postValue(VpnState.CONNECTED);

                startPacketLoop(vpnInterface, prefs, dbHelper);
                Log.i(TAG, "AdShield VPN tunnel established successfully");
            } else {
                Log.e(TAG, "Failed to establish VPN interface");
                stopVpn();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting VPN: " + e.getMessage(), e);
            stopVpn();
        }
    }

    private void startPacketLoop(ParcelFileDescriptor pfd, PreferencesManager prefs, AdShieldDbHelper dbHelper) {
        isRunning = true;
        executor = Executors.newFixedThreadPool(4);

        String[] upstream = prefs.getUpstreamDnsServers();
        CloudflareDnsForwarder forwarder = new CloudflareDnsForwarder(this, upstream[0], upstream[1]);
        DomainBlocklist blocklist = new DomainBlocklist(dbHelper, prefs);

        filteringEngine = new DnsFilteringEngine(this, blocklist, forwarder, dbHelper, prefs);
        filteringEngine.setQueryListener(entry -> {
            if (entry.isBlocked()) {
                long updated = prefs.getBlockedRequestsCount() + 1;
                prefs.setBlockedRequestsCount(updated);
                blockedCountLiveData.postValue(updated);
                updateNotification(updated);
            }
            long total = prefs.getTotalQueriesCount() + 1;
            prefs.setTotalQueriesCount(total);
            totalQueriesLiveData.postValue(total);
        });

        workerThread = new Thread(() -> {
            // Large community list must be in memory before the first query is answered
            BlocklistStore.getInstance().ensureLoaded(getApplicationContext());

            FileInputStream in = new FileInputStream(pfd.getFileDescriptor());
            FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor());
            byte[] buffer = new byte[32767];

            try {
                while (isRunning) {
                    int length = in.read(buffer);
                    if (length <= 0) break;

                    byte[] packetCopy = new byte[length];
                    System.arraycopy(buffer, 0, packetCopy, 0, length);

                    if (executor != null && !executor.isShutdown()) {
                        executor.execute(() -> {
                            try {
                                byte[] response = filteringEngine.processPacket(packetCopy, length);
                                if (response != null && isRunning) {
                                    synchronized (out) {
                                        out.write(response);
                                        out.flush();
                                    }
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Error in packet processor: " + e.getMessage());
                            }
                        });
                    }
                }
            } catch (IOException e) {
                if (isRunning) {
                    Log.w(TAG, "TUN read error: " + e.getMessage());
                }
            } finally {
                Log.i(TAG, "TUN packet worker loop ended");
            }
        }, "AdShield-TunWorker");

        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void stopVpn() {
        vpnStateLiveData.postValue(VpnState.DISCONNECTING);
        teardownTunnel();

        PreferencesManager prefs = AdShieldApplication.getInstance().getPreferencesManager();
        if (prefs != null) {
            prefs.setProtectionEnabled(false);
        }

        vpnStateLiveData.postValue(VpnState.DISCONNECTED);

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        Log.i(TAG, "AdShield VPN stopped");
    }

    /** Closes the TUN interface and worker threads without stopping the service. */
    private void teardownTunnel() {
        isRunning = false;

        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException ignored) {}
            vpnInterface = null;
        }

        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }

        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }

        filteringEngine = null;
        protectionStartTimeMillis = null;
    }

    @Override
    public void onRevoke() {
        Log.w(TAG, "VPN permission revoked");
        stopVpn();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stopVpn();
        SpotifyAdMuter.unregister();
        super.onDestroy();
    }

    private void updateNotification(long blockedCount) {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, buildForegroundNotification());
            }
        } catch (Exception ignored) {}
    }

    private Notification buildForegroundNotification() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(
                this, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, AdShieldVpnService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PreferencesManager prefs = AdShieldApplication.getInstance().getPreferencesManager();
        long blocked = (prefs != null) ? prefs.getBlockedRequestsCount() : 0;
        String contentText = (blocked > 0)
                ? (blocked + " ads & trackers blocked")
                : getString(R.string.vpn_notification_text);

        return new NotificationCompat.Builder(this, AdShieldApplication.CHANNEL_ID)
                .setContentTitle(getString(R.string.vpn_notification_title))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(mainPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_notification, getString(R.string.vpn_action_stop), stopPendingIntent)
                .build();
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, AdShieldVpnService.class);
        intent.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, AdShieldVpnService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }
}
