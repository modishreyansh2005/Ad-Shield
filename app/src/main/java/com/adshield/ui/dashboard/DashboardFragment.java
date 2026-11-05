package com.adshield.ui.dashboard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
        binding.btnPower.setOnClickListener(v -> toggleProtection());
        binding.btnToggleProtection.setOnClickListener(v -> toggleProtection());

        binding.btnViewAllLogs.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateTo(R.id.nav_logs);
            }
        });

        binding.cardStreamingHub.setOnClickListener(v -> openStreamingHub(com.adshield.ui.player.CyberPlayerActivity.TARGET_YOUTUBE));
        binding.btnLaunchYoutube.setOnClickListener(v -> openStreamingHub(com.adshield.ui.player.CyberPlayerActivity.TARGET_YOUTUBE));
        binding.btnLaunchSpotify.setOnClickListener(v -> openStreamingHub(com.adshield.ui.player.CyberPlayerActivity.TARGET_SPOTIFY));
    }

    private void openStreamingHub(String target) {
        android.content.Intent intent = new android.content.Intent(requireContext(), com.adshield.ui.player.CyberPlayerActivity.class);
        intent.putExtra(com.adshield.ui.player.CyberPlayerActivity.EXTRA_TARGET, target);
        startActivity(intent);
    }

    private void toggleProtection() {
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
    }

    private void observeVpnState() {
        AdShieldVpnService.getVpnStateLiveData().observe(getViewLifecycleOwner(), state -> {
            if (state == null) state = VpnState.DISCONNECTED;
            updateVpnStateUi(state);
        });
    }

    private void observeCounts() {
        AdShieldVpnService.getBlockedCountLiveData().observe(getViewLifecycleOwner(), count -> refreshStats());
        AdShieldVpnService.getTotalQueriesLiveData().observe(getViewLifecycleOwner(), total -> refreshStats());
    }

    private void updateVpnStateUi(VpnState state) {
        if (!isAdded() || binding == null) return;
        Context ctx = requireContext();

        switch (state) {
            case CONNECTED:
                binding.btnPower.setBackgroundResource(R.drawable.bg_power_button_on);
                binding.viewGlowRing.setVisibility(View.VISIBLE);
                binding.tvProtectionStatus.setText(R.string.status_protected);
                binding.tvProtectionStatus.setBackgroundResource(R.drawable.bg_badge_success);
                binding.tvProtectionStatus.setTextColor(ctx.getColor(R.color.brand_secondary));

                binding.btnToggleProtection.setText("Turn off protection");
                binding.btnToggleProtection.setIconResource(R.drawable.ic_power);
                binding.btnToggleProtection.setBackgroundColor(Color.parseColor("#1A253A"));
                binding.btnToggleProtection.setStrokeColor(ColorStateList.valueOf(ctx.getColor(R.color.status_blocked)));
                binding.btnToggleProtection.setStrokeWidth(2);
                binding.btnToggleProtection.setTextColor(ctx.getColor(R.color.status_blocked));
                binding.btnToggleProtection.setIconTint(ColorStateList.valueOf(ctx.getColor(R.color.status_blocked)));
                break;
            case CONNECTING:
                binding.btnPower.setBackgroundResource(R.drawable.bg_power_button_off);
                binding.viewGlowRing.setVisibility(View.GONE);
                binding.tvProtectionStatus.setText(R.string.status_connecting);
                binding.tvProtectionStatus.setBackgroundResource(R.drawable.bg_badge_warning);
                binding.tvProtectionStatus.setTextColor(ctx.getColor(R.color.status_warning));
                binding.tvProtectionUptime.setText("Configuring secure DNS routing...");

                binding.btnToggleProtection.setText("Connecting...");
                binding.btnToggleProtection.setIconResource(R.drawable.ic_shield_check);
                binding.btnToggleProtection.setBackgroundColor(ctx.getColor(R.color.brand_secondary));
                binding.btnToggleProtection.setStrokeWidth(0);
                binding.btnToggleProtection.setTextColor(Color.parseColor("#0B1220"));
                binding.btnToggleProtection.setIconTint(ColorStateList.valueOf(Color.parseColor("#0B1220")));
                break;
            case DISCONNECTED:
            default:
                binding.btnPower.setBackgroundResource(R.drawable.bg_power_button_off);
                binding.viewGlowRing.setVisibility(View.GONE);
                binding.tvProtectionStatus.setText(R.string.status_inactive);
                binding.tvProtectionStatus.setBackgroundResource(R.drawable.bg_badge_warning);
                binding.tvProtectionStatus.setTextColor(ctx.getColor(R.color.status_warning));
                binding.tvProtectionUptime.setText("Tap below to activate system-wide protection");

                binding.btnToggleProtection.setText("Turn on protection");
                binding.btnToggleProtection.setIconResource(R.drawable.ic_shield_check);
                binding.btnToggleProtection.setBackgroundColor(ctx.getColor(R.color.brand_secondary));
                binding.btnToggleProtection.setStrokeWidth(0);
                binding.btnToggleProtection.setTextColor(Color.parseColor("#0B1220"));
                binding.btnToggleProtection.setIconTint(ColorStateList.valueOf(Color.parseColor("#0B1220")));
                break;
        }
    }

    public void refreshStats() {
        if (!isAdded() || binding == null) return;

        long blocked = prefsManager.getBlockedRequestsCount();
        long total = prefsManager.getTotalQueriesCount();

        binding.tvStatBlocked.setText(String.format(Locale.US, "%,d", blocked));
        binding.tvStatTotal.setText(String.format(Locale.US, "%,d", total));

        float percentage = (total > 0) ? ((float) blocked / total * 100f) : 0f;
        binding.tvStatPercentage.setText(String.format(Locale.US, "%.1f%%", percentage));

        long[] today = dbHelper.getTodayCounts();
        binding.tvStatToday.setText(String.format(Locale.US, "%,d", today[1]));

        // Upstream DNS display
        String dnsKey = prefsManager.getDnsProvider();
        String dnsName = "Cloudflare (1.1.1.1)";
        if ("google".equalsIgnoreCase(dnsKey)) {
            dnsName = "Google (8.8.8.8)";
        } else if ("quad9".equalsIgnoreCase(dnsKey)) {
            dnsName = "Quad9 (9.9.9.9)";
        } else if ("adguard".equalsIgnoreCase(dnsKey)) {
            dnsName = "AdGuard (94.140.14.14)";
        }
        binding.tvDnsIndicator.setText("DNS: " + dnsName);

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
                if (binding != null) {
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
