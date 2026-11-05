package com.adshield.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.util.Log;

import com.adshield.AdShieldApplication;
import com.adshield.data.PreferencesManager;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(TAG, "Boot completed broadcast received");
            PreferencesManager prefs = AdShieldApplication.getInstance().getPreferencesManager();
            if (prefs != null && prefs.isStartOnBoot() && prefs.isProtectionEnabled()) {
                Intent prepareIntent = VpnService.prepare(context);
                if (prepareIntent == null) {
                    Log.i(TAG, "Starting AdShield VPN on device boot");
                    AdShieldVpnService.start(context);
                } else {
                    Log.w(TAG, "VPN permission required before auto-starting on boot");
                }
            }
        }
    }
}
