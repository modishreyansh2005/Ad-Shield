package com.adshield;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

import com.adshield.data.PreferencesManager;
import com.adshield.data.db.AdShieldDbHelper;
import com.adshield.dns.BlocklistStore;

public class AdShieldApplication extends Application {

    public static final String CHANNEL_ID = "adshield_vpn_channel";

    private static volatile AdShieldApplication instance;
    private PreferencesManager preferencesManager;
    private AdShieldDbHelper dbHelper;

    public static AdShieldApplication getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        preferencesManager = new PreferencesManager(this);
        dbHelper = AdShieldDbHelper.getInstance(this);

        // Apply saved theme preference (Light, Dark, or System Default)
        AppCompatDelegate.setDefaultNightMode(preferencesManager.getThemeMode());

        createNotificationChannel();

        // Parse the ~80k-domain community blocklist off the main thread at startup
        BlocklistStore.getInstance().loadAsync(this);
    }

    public PreferencesManager getPreferencesManager() {
        return preferencesManager;
    }

    public AdShieldDbHelper getDbHelper() {
        return dbHelper;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.vpn_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.vpn_channel_desc));
            channel.setShowBadge(false);

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }
}
