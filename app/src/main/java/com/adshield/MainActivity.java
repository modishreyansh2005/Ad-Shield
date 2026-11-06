package com.adshield;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.adshield.data.PreferencesManager;
import com.adshield.databinding.ActivityMainBinding;
import com.adshield.service.AdShieldVpnService;
import com.adshield.ui.apps.AppSelectionFragment;
import com.adshield.ui.dashboard.DashboardFragment;
import com.adshield.ui.filters.FilterManagementFragment;
import com.adshield.ui.settings.SettingsFragment;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private PreferencesManager prefsManager;

    private final ActivityResultLauncher<Intent> vpnPrepareLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    launchVpnService();
                } else {
                    Toast.makeText(this, "AdShield needs this permission to block ads.", Toast.LENGTH_LONG).show();
                }
            }
    );

    private final ActivityResultLauncher<String> notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                // Handled gracefully, notification channel will display service status
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefsManager = new PreferencesManager(this);
        AppCompatDelegate.setDefaultNightMode(prefsManager.getThemeMode());

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        requestNotificationPermissionIfNeeded();
        setupNavigation();

        if (savedInstanceState == null) {
            switchFragment(new DashboardFragment(), "Dashboard");
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                switchFragment(new DashboardFragment(), "Dashboard");
                return true;
            } else if (id == R.id.nav_apps) {
                switchFragment(new AppSelectionFragment(), "Apps");
                return true;
            } else if (id == R.id.nav_filters) {
                switchFragment(new FilterManagementFragment(), "Filters");
                return true;
            } else if (id == R.id.nav_settings) {
                switchFragment(new SettingsFragment(), "Settings");
                return true;
            }
            return false;
        });
    }

    public void navigateTo(int menuItemId) {
        binding.bottomNavigation.setSelectedItemId(menuItemId);
    }

    private void switchFragment(Fragment fragment, String tag) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.nav_host_fragment, fragment, tag);
        transaction.commit();
    }

    public void startProtection() {
        Intent prepareIntent = VpnService.prepare(this);
        if (prepareIntent != null) {
            vpnPrepareLauncher.launch(prepareIntent);
        } else {
            launchVpnService();
        }
    }

    private void launchVpnService() {
        prefsManager.setProtectionEnabled(true);
        Intent intent = new Intent(this, AdShieldVpnService.class);
        intent.setAction(AdShieldVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    public void stopProtection() {
        prefsManager.setProtectionEnabled(false);
        Intent intent = new Intent(this, AdShieldVpnService.class);
        intent.setAction(AdShieldVpnService.ACTION_STOP);
        startService(intent);
    }
}
