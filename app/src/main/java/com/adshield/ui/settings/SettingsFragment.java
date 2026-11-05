package com.adshield.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.adshield.R;
import com.adshield.data.PreferencesManager;
import com.adshield.databinding.FragmentSettingsBinding;
import com.adshield.service.AdShieldVpnService;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private PreferencesManager prefsManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        prefsManager = new PreferencesManager(context);

        setupThemePicker();
        setupDnsPicker();
        setupShieldSwitches();
        setupOutsideSkipperSettings();
        setupResetButton();
    }

    private void setupOutsideSkipperSettings() {
        binding.switchAutoSkipper.setChecked(prefsManager.isAutoSkipperEnabled());
        binding.switchAutoSkipper.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsManager.setAutoSkipperEnabled(isChecked);
        });

        binding.switchAutoMute.setChecked(prefsManager.isAutoMuteAdsEnabled());
        binding.switchAutoMute.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsManager.setAutoMuteAdsEnabled(isChecked);
        });

        updateAccessibilitySummary();
        binding.cardAccessibilitySettings.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
                Toast.makeText(requireContext(), "Turn ON 'AdShield Video Ad Auto-Skipper'", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Could not open Accessibility Settings", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateAccessibilitySummary() {
        if (getContext() == null || binding == null) return;
        boolean isRunning = PreferencesManager.isAccessibilityServiceRunning(getContext());
        if (isRunning) {
            binding.tvAccessibilityServiceStatus.setText("Granted & Active • Auto-Skipping Running");
            binding.tvAccessibilityServiceStatus.setTextColor(requireContext().getColor(R.color.brand_secondary));
        } else {
            binding.tvAccessibilityServiceStatus.setText("Permission Required • Tap to enable in Settings");
            binding.tvAccessibilityServiceStatus.setTextColor(requireContext().getColor(R.color.status_warning));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAccessibilitySummary();
    }

    private void setupThemePicker() {
        updateThemeSummary();
        binding.cardSettingTheme.setOnClickListener(v -> {
            String[] options = {"System Default", "Light Mode", "Dark Mode (Cybersecurity)"};
            int currentMode = prefsManager.getThemeMode();
            int selectedIndex = 0;
            if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) {
                selectedIndex = 1;
            } else if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
                selectedIndex = 2;
            }

            new AlertDialog.Builder(requireContext())
                    .setTitle("Select App Theme")
                    .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                        int targetMode;
                        if (which == 1) {
                            targetMode = AppCompatDelegate.MODE_NIGHT_NO;
                        } else if (which == 2) {
                            targetMode = AppCompatDelegate.MODE_NIGHT_YES;
                        } else {
                            targetMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                        }

                        prefsManager.setThemeMode(targetMode);
                        AppCompatDelegate.setDefaultNightMode(targetMode);
                        updateThemeSummary();
                        dialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void updateThemeSummary() {
        int mode = prefsManager.getThemeMode();
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) {
            binding.tvCurrentTheme.setText("Light Mode");
        } else if (mode == AppCompatDelegate.MODE_NIGHT_YES) {
            binding.tvCurrentTheme.setText("Dark Mode (Cybersecurity)");
        } else {
            binding.tvCurrentTheme.setText("System Default");
        }
    }

    private void setupDnsPicker() {
        updateDnsSummary();
        binding.cardSettingDns.setOnClickListener(v -> {
            String[] providers = {
                    "Cloudflare DNS (1.1.1.1 / 1.0.0.1)",
                    "Google Public DNS (8.8.8.8 / 8.8.4.4)",
                    "Quad9 Security DNS (9.9.9.9)",
                    "AdGuard DNS (94.140.14.14)"
            };
            String[] keys = {"cloudflare", "google", "quad9", "adguard"};

            String currentKey = prefsManager.getDnsProvider();
            int selected = 0;
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equalsIgnoreCase(currentKey)) {
                    selected = i;
                    break;
                }
            }

            new AlertDialog.Builder(requireContext())
                    .setTitle("Choose Upstream DNS Provider")
                    .setSingleChoiceItems(providers, selected, (dialog, which) -> {
                        String newKey = keys[which];
                        prefsManager.setDnsProvider(newKey);
                        updateDnsSummary();

                        // Reconnect active VPN service if running
                        Context ctx = requireContext();
                        Intent reconnectIntent = new Intent(ctx, AdShieldVpnService.class);
                        reconnectIntent.setAction(AdShieldVpnService.ACTION_RECONNECT);
                        ctx.startService(reconnectIntent);

                        dialog.dismiss();
                        Toast.makeText(ctx, "DNS Provider set to " + providers[which], Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void updateDnsSummary() {
        String key = prefsManager.getDnsProvider();
        switch (key.toLowerCase()) {
            case "google":
                binding.tvCurrentDns.setText("Google DNS (8.8.8.8)");
                break;
            case "quad9":
                binding.tvCurrentDns.setText("Quad9 DNS (9.9.9.9)");
                break;
            case "adguard":
                binding.tvCurrentDns.setText("AdGuard DNS (94.140.14.14)");
                break;
            case "cloudflare":
            default:
                binding.tvCurrentDns.setText("Cloudflare DNS (1.1.1.1)");
                break;
        }
    }

    private void setupShieldSwitches() {
        binding.switchYoutubeShield.setChecked(prefsManager.isYouTubeProtectionEnabled());
        binding.switchYoutubeShield.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsManager.setYouTubeProtectionEnabled(isChecked);
        });

        binding.switchSpotifyShield.setChecked(prefsManager.isSpotifyProtectionEnabled());
        binding.switchSpotifyShield.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsManager.setSpotifyProtectionEnabled(isChecked);
        });
    }

    private void setupResetButton() {
        binding.btnResetStats.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Reset Statistics")
                    .setMessage("Are you sure you want to reset all query and ad-blocking counters?")
                    .setPositiveButton("Reset", (dialog, which) -> {
                        prefsManager.resetStatistics();
                        Toast.makeText(requireContext(), "Statistics have been reset.", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
