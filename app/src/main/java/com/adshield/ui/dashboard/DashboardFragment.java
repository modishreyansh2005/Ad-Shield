package com.adshield.ui.dashboard;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.adshield.MainActivity;
import com.adshield.R;
import com.adshield.data.PreferencesManager;
import com.adshield.data.db.AdShieldDbHelper;
import com.adshield.data.model.DnsLogEntry;
import com.adshield.data.model.VpnState;
import com.adshield.databinding.FragmentDashboardBinding;
import com.adshield.service.AdShieldVpnService;
import com.adshield.ui.dialogs.AppStatusDialog;
import com.adshield.ui.logs.QueryLogAdapter;

import java.util.List;
import java.util.Locale;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private PreferencesManager prefsManager;
    private AdShieldDbHelper dbHelper;
    private QueryLogAdapter recentBlockedAdapter;
    private final Handler uptimeHandler = new Handler(Looper.getMainLooper());
    private Runnable uptimeRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        prefsManager = new PreferencesManager(context);
        dbHelper = AdShieldDbHelper.getInstance(context);

        setupRecyclerView();
        setupClickListeners();
        observeVpnState();
        observeCounts();
        refreshStats();
        startUptimeTicker();
    }

    private void setupRecyclerView() {
        recentBlockedAdapter = new QueryLogAdapter();
        binding.recyclerRecentBlocked.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerRecentBlocked.setAdapter(recentBlockedAdapter);
    }

    private void setupClickListeners() {
        binding.btnPower.setOnClickListener(v -> {
            VpnState currentState = AdShieldVpnService.getVpnStateLiveData().getValue();
            if (currentState == VpnState.CONNECTED || currentState == VpnState.CONNECTING) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).stopProtection();
                }
            } else {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).startProtection();
                }
            }
        });

        binding.btnQuickSettings.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateTo(R.id.nav_settings);
            }
        });

        binding.btnViewAllLogs.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateTo(R.id.nav_logs);
            }
        });

        binding.cardYoutubeStatus.setOnClickListener(v -> {
            AppStatusDialog.show(getChildFragmentManager(), AppStatusDialog.AppTarget.YOUTUBE);
        });

        binding.cardHotstarStatus.setOnClickListener(v -> {
            AppStatusDialog.show(getChildFragmentManager(), AppStatusDialog.AppTarget.HOTSTAR);
        });

        binding.cardSpotifyStatus.setOnClickListener(v -> {
            AppStatusDialog.show(getChildFragmentManager(), AppStatusDialog.AppTarget.SPOTIFY);
        });

        binding.btnSetupSkipper.setOnClickListener(v -> openAccessibilitySettings());
        binding.cardOutsideSkipper.setOnClickListener(v -> openAccessibilitySettings());

        binding.cardLaunchCyberHub.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(requireContext(), com.adshield.ui.player.CyberPlayerActivity.class);
            startActivity(intent);
        });
    }

    private void openAccessibilitySettings() {
        try {
            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            android.widget.Toast.makeText(requireContext(),
                    "Find and turn ON 'AdShield Outside-App Ad Skipper'",
                    android.widget.Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            android.widget.Toast.makeText(requireContext(), "Unable to open Accessibility Settings", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void observeVpnState() {
        AdShieldVpnService.getVpnStateLiveData().observe(getViewLifecycleOwner(), state -> {
            if (state == null) state = VpnState.DISCONNECTED;
            updateVpnStateUi(state);
        });
    }

    private void observeCounts() {
        AdShieldVpnService.getBlockedCountLiveData().observe(getViewLifecycleOwner(), count -> {
            refreshStats();
        });

        AdShieldVpnService.getTotalQueriesLiveData().observe(getViewLifecycleOwner(), total -> {
            refreshStats();
        });
    }

    private void updateVpnStateUi(VpnState state) {
        switch (state) {
            case CONNECTED:
                binding.btnPower.setBackgroundResource(R.drawable.bg_power_button_on);
                binding.viewGlowRing.setVisibility(View.VISIBLE);
                binding.tvProtectionStatus.setText(R.string.status_protected);
                binding.tvProtectionStatus.setTextColor(requireContext().getColor(R.color.brand_secondary));
                break;
            case CONNECTING:
                binding.btnPower.setBackgroundResource(R.drawable.bg_power_button_off);
                binding.viewGlowRing.setVisibility(View.GONE);
                binding.tvProtectionStatus.setText(R.string.status_connecting);
                binding.tvProtectionStatus.setTextColor(requireContext().getColor(R.color.status_warning));
                binding.tvProtectionUptime.setText("Configuring secure DNS routing...");
                break;
            case DISCONNECTED:
            default:
                binding.btnPower.setBackgroundResource(R.drawable.bg_power_button_off);
                binding.viewGlowRing.setVisibility(View.GONE);
                binding.tvProtectionStatus.setText(R.string.status_inactive);
                binding.tvProtectionStatus.setTextColor(requireContext().getColor(R.color.status_warning));
                binding.tvProtectionUptime.setText("Tap to enable system-wide ad protection");
                break;
        }
    }

    public void refreshStats() {
        if (!isAdded()) return;

        long blocked = prefsManager.getBlockedRequestsCount();
        long total = prefsManager.getTotalQueriesCount();

        binding.tvStatBlocked.setText(String.format(Locale.US, "%,d", blocked));
        binding.tvStatTotal.setText(String.format(Locale.US, "%,d", total));

        float percentage = (total > 0) ? ((float) blocked / total * 100f) : 0f;
        binding.tvStatPercentage.setText(String.format(Locale.US, "%.1f%%", percentage));

        long[] today = dbHelper.getTodayCounts();
        binding.tvStatToday.setText(String.format(Locale.US, "%,d", today[1]));

        long ytCount = prefsManager.getYouTubeBlockedCount();
        binding.tvYtBlockedCount.setText(String.format(Locale.US, "%d blocked", ytCount));

        long hotstarCount = prefsManager.getHotstarBlockedCount();
        binding.tvHotstarBlockedCount.setText(String.format(Locale.US, "%d blocked", hotstarCount));

        long spCount = prefsManager.getSpotifyBlockedCount();
        binding.tvSpotifyBlockedCount.setText(String.format(Locale.US, "%d blocked", spCount));

        // Outside-App Skipper Status
        boolean isSkipperRunning = PreferencesManager.isAccessibilityServiceRunning(requireContext());
        if (isSkipperRunning) {
            binding.tvSkipperStatusBadge.setText("ACTIVE • Auto-Skipping Outside Apps");
            binding.tvSkipperStatusBadge.setTextColor(requireContext().getColor(R.color.cyber_neon_green));
            binding.btnSetupSkipper.setText("Outside-App Protection Active");
            binding.btnSetupSkipper.setIconResource(R.drawable.ic_check);
        } else {
            binding.tvSkipperStatusBadge.setText("SETUP REQUIRED • Tap to Enable");
            binding.tvSkipperStatusBadge.setTextColor(requireContext().getColor(R.color.status_warning));
            binding.btnSetupSkipper.setText("Enable Outside-App Auto Skipper");
            binding.btnSetupSkipper.setIconResource(R.drawable.ic_settings);
        }
        long skippedAds = prefsManager.getAutoSkippedAdsCount();
        binding.tvAutoSkippedCount.setText(String.format(Locale.US, "%d Skipped", skippedAds));

        // Load recent blocked requests (up to 4)
        List<DnsLogEntry> recentBlocked = dbHelper.getRecentLogs(4, null, true);
        if (recentBlocked.isEmpty()) {
            binding.tvNoThreats.setVisibility(View.VISIBLE);
            binding.recyclerRecentBlocked.setVisibility(View.GONE);
        } else {
            binding.tvNoThreats.setVisibility(View.GONE);
            binding.recyclerRecentBlocked.setVisibility(View.VISIBLE);
            recentBlockedAdapter.setItems(recentBlocked);
        }
    }

    private void startUptimeTicker() {
        uptimeRunnable = new Runnable() {
            @Override
            public void run() {
                VpnState state = AdShieldVpnService.getVpnStateLiveData().getValue();
                if (state == VpnState.CONNECTED) {
                    Long start = AdShieldVpnService.getProtectionStartTimeMillis();
                    if (start != null) {
                        long elapsedSec = (System.currentTimeMillis() - start) / 1000L;
                        long hours = elapsedSec / 3600;
                        long minutes = (elapsedSec % 3600) / 60;
                        long seconds = elapsedSec % 60;
                        binding.tvProtectionUptime.setText(String.format(Locale.US,
                                "Active for %02d:%02d:%02d • Filtering Active", hours, minutes, seconds));
                    }
                }
                uptimeHandler.postDelayed(this, 1000L);
            }
        };
        uptimeHandler.post(uptimeRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStats();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        uptimeHandler.removeCallbacks(uptimeRunnable);
        binding = null;
    }
}
