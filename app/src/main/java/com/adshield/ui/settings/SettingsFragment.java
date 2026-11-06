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
import com.adshield.data.model.VpnState;
import com.adshield.databinding.FragmentSettingsBinding;
import com.adshield.service.AdShieldVpnService;
import com.adshield.service.MediaAdMuteService;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private PreferencesManager prefsManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
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
        setupSpotifyMuteSetting();
        setupResetButton();
    }

    private void setupSpotifyMuteSetting() {
        binding.switchAutoMute.setChecked(prefsManager.isAutoMuteAdsEnabled());
        binding.switchAutoMute.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefsManager.setAutoMuteAdsEnabled(isChecked);
        });

        binding.cardNotificationAccess.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                Toast.makeText(requireContext(), "Turn on 'AdShield Ad Mute'", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Couldn't open the setting. Find it in Settings → Notifications → Device & app notifications.", Toast.LENGTH_LONG).show();
            }
        });
        updateNotificationAccessSummary();
    }

    private void updateNotificationAccessSummary() {
        if (getContext() == null || binding == null) return;
        if (MediaAdMuteService.isAccessGranted(requireContext())) {
            binding.tvNotificationAccessStatus.setText("On • Ads will be muted");
            binding.tvNotificationAccessStatus.setTextColor(requireContext().getColor(R.color.brand_secondary));
        } else {
            binding.tvNotificationAccessStatus.setText("Off • Tap to turn on");
            binding.tvNotificationAccessStatus.setTextColor(requireContext().getColor(R.color.status_warning));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateNotificationAccessSummary();
    }

    private void setupThemePicker() {
        updateThemeSummary();
        binding.cardSettingTheme.setOnClickListener(v -> {
            String[] options = {"Same as phone", "Light", "Dark"};
            int currentMode = prefsManager.getThemeMode();
            int selectedIndex = 0;
            if (currentMode == AppCompatDelegate.MODE_NIGHT_NO) {
                selectedIndex = 1;
            } else if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
                selectedIndex = 2;
            }

            new AlertDialog.Builder(requireContext())
                    .setTitle("Theme")
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
            binding.tvCurrentTheme.setText("Light");
        } else if (mode == AppCompatDelegate.MODE_NIGHT_YES) {
            binding.tvCurrentTheme.setText("Dark");
        } else {
            binding.tvCurrentTheme.setText("Same as phone");
        }
    }

    private void setupDnsPicker() {
        updateDnsSummary();
        binding.cardSettingDns.setOnClickListener(v -> {
            String[] providers = {
                    "Cloudflare (fast, recommended)",
                    "Google",
                    "Quad9 (also blocks harmful sites)",
                    "AdGuard (blocks even more ads)"
            };
            String[] keys = { "cloudflare", "google", "quad9", "adguard" };

            String currentKey = prefsManager.getDnsProvider();
            int selected = 0;
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equalsIgnoreCase(currentKey)) {
                    selected = i;
                    break;
                }
            }

            new AlertDialog.Builder(requireContext())
                    .setTitle("Lookup service")
                    .setSingleChoiceItems(providers, selected, (dialog, which) -> {
                        String newKey = keys[which];
                        prefsManager.setDnsProvider(newKey);
                        updateDnsSummary();

                        // Reconnect only if protection is on; otherwise this would start the VPN unasked
                        Context ctx = requireContext();
                        if (AdShieldVpnService.getVpnStateLiveData().getValue() == VpnState.CONNECTED) {
                            Intent reconnectIntent = new Intent(ctx, AdShieldVpnService.class);
                            reconnectIntent.setAction(AdShieldVpnService.ACTION_RECONNECT);
                            ctx.startService(reconnectIntent);
                        }

                        dialog.dismiss();
                        Toast.makeText(ctx, "Now using " + providers[which], Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void updateDnsSummary() {
        String key = prefsManager.getDnsProvider();
        switch (key.toLowerCase(java.util.Locale.ROOT)) {
            case "google":
                binding.tvCurrentDns.setText("Google");
                break;
            case "quad9":
                binding.tvCurrentDns.setText("Quad9 (also blocks harmful sites)");
                break;
            case "adguard":
                binding.tvCurrentDns.setText("AdGuard (blocks even more ads)");
                break;
            case "cloudflare":
            default:
                binding.tvCurrentDns.setText("Cloudflare (recommended)");
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
                    .setTitle("Reset counters")
                    .setMessage("Set all ad-blocking counters back to zero?")
                    .setPositiveButton("Reset", (dialog, which) -> {
                        prefsManager.resetStatistics();
                        Toast.makeText(requireContext(), "Counters reset.", Toast.LENGTH_SHORT).show();
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
