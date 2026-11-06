package com.adshield.ui.dashboard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.adshield.MainActivity;
import com.adshield.R;
import com.adshield.data.PreferencesManager;
import com.adshield.data.db.AdShieldDbHelper;
import com.adshield.data.model.VpnState;
import com.adshield.databinding.FragmentDashboardBinding;
import com.adshield.service.AdShieldVpnService;

import java.util.Locale;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private PreferencesManager prefsManager;
    private AdShieldDbHelper dbHelper;
    private final Handler uptimeHandler = new Handler(Looper.getMainLooper());
    private Runnable uptimeRunnable;
    private android.animation.ObjectAnimator glowPulse;

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

        setupClickListeners();
        observeVpnState();
        observeCounts();
        refreshStats();
        startUptimeTicker();
    }

    private void setupClickListeners() {
        binding.btnPower.setOnClickListener(v -> {
            // Quick press "bounce" so the tap feels physical
            v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(160).start())
                    .start();
            toggleProtection();
        });

        binding.cardStreamingHub.setOnClickListener(v -> openStreamingHub(com.adshield.ui.player.CyberPlayerActivity.TARGET_YOUTUBE));
        binding.btnLaunchYoutube.setOnClickListener(v -> openStreamingHub(com.adshield.ui.player.CyberPlayerActivity.TARGET_YOUTUBE));
        binding.btnLaunchSpotify.setOnClickListener(v -> openStreamingHub(com.adshield.ui.player.CyberPlayerActivity.TARGET_SPOTIFY));

        binding.cardPrivateDns.setOnClickListener(v -> {
            try {
                startActivity(new android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
            } catch (Exception ignored) {}
        });
    }

    private void updatePrivateDnsWarning() {
        if (!isAdded() || binding == null) return;
        Context ctx = requireContext();
        String strictHost = PreferencesManager.getStrictPrivateDnsHost(ctx);
        if (strictHost != null) {
            binding.tvPrivateDnsTip.setText("Your phone's Private DNS setting (" + strictHost + ") stops AdShield from "
                    + "blocking ads. Tap here, open Private DNS and choose Off or Automatic.");
            binding.tvPrivateDnsTip.setTextColor(ctx.getColor(R.color.status_warning));
            binding.ivPrivateDnsIcon.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.status_warning)));
            binding.cardPrivateDns.setStrokeColor(ColorStateList.valueOf(ctx.getColor(R.color.status_warning)));
        } else {
            binding.tvPrivateDnsTip.setText("Tip: For best results, set Private DNS to Off or Automatic in your phone's settings.");
            android.util.TypedValue tv = new android.util.TypedValue();
            ctx.getTheme().resolveAttribute(R.attr.theme_text_secondary, tv, true);
            binding.tvPrivateDnsTip.setTextColor(tv.data);
            binding.ivPrivateDnsIcon.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.brand_primary)));
            ctx.getTheme().resolveAttribute(R.attr.theme_border, tv, true);
            binding.cardPrivateDns.setStrokeColor(ColorStateList.valueOf(tv.data));
        }
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
                binding.btnPower.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.power_on_icon)));
                applyButtonShadow(ctx, 18f, R.color.power_on_shadow);
                startGlowPulse();
                binding.tvProtectionStatus.setText(R.string.status_protected);
                binding.tvProtectionStatus.setBackgroundResource(R.drawable.bg_badge_success);
                binding.tvProtectionStatus.setTextColor(ctx.getColor(R.color.brand_secondary));
                updateUptimeText();
                break;
            case CONNECTING:
                binding.btnPower.setBackgroundResource(R.drawable.bg_power_button_off);
                binding.btnPower.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.brand_secondary)));
                applyButtonShadow(ctx, 10f, R.color.power_off_shadow);
                stopGlowPulse();
                binding.tvProtectionStatus.setText(R.string.status_connecting);
                binding.tvProtectionStatus.setBackgroundResource(R.drawable.bg_badge_warning);
                binding.tvProtectionStatus.setTextColor(ctx.getColor(R.color.status_warning));
                binding.tvProtectionUptime.setText("Turning on…");
                break;
            case DISCONNECTED:
            default:
                binding.btnPower.setBackgroundResource(R.drawable.bg_power_button_off);
                binding.btnPower.setImageTintList(ColorStateList.valueOf(ctx.getColor(R.color.power_off_icon)));
                applyButtonShadow(ctx, 10f, R.color.power_off_shadow);
                stopGlowPulse();
                binding.tvProtectionStatus.setText(R.string.status_inactive);
                binding.tvProtectionStatus.setBackgroundResource(R.drawable.bg_badge_warning);
                binding.tvProtectionStatus.setTextColor(ctx.getColor(R.color.status_warning));
                binding.tvProtectionUptime.setText("Tap to turn on");
                break;
        }
    }

    /** Soft lift under the power button; tinted to the button's colour on Android 9+. */
    private void applyButtonShadow(Context ctx, float elevationDp, int colorRes) {
        binding.btnPower.setElevation(elevationDp * ctx.getResources().getDisplayMetrics().density);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            int color = ctx.getColor(colorRes);
            binding.btnPower.setOutlineSpotShadowColor(color);
            binding.btnPower.setOutlineAmbientShadowColor(color);
        }
    }

    /** Slow "breathing" glow behind the button while protection is on. */
    private void startGlowPulse() {
        View glow = binding.viewGlowRing;
        if (glow.getVisibility() == View.VISIBLE && glowPulse != null) return;
        glow.setVisibility(View.VISIBLE);
        glowPulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(glow,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.88f, 1.05f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.88f, 1.05f),
                android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.55f));
        glowPulse.setDuration(1600);
        glowPulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        glowPulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        glowPulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        glowPulse.start();
    }

    private void stopGlowPulse() {
        if (glowPulse != null) {
            glowPulse.cancel();
            glowPulse = null;
        }
        binding.viewGlowRing.setVisibility(View.GONE);
    }

    private void updateUptimeText() {
        Long start = AdShieldVpnService.getProtectionStartTimeMillis();
        if (start == null) {
            binding.tvProtectionUptime.setText("Tap to turn off");
            return;
        }
        long elapsedSec = (System.currentTimeMillis() - start) / 1000L;
        binding.tvProtectionUptime.setText(String.format(Locale.US, "On for %02d:%02d:%02d · Tap to turn off",
                elapsedSec / 3600, (elapsedSec % 3600) / 60, elapsedSec % 60));
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
    }

    private void startUptimeTicker() {
        uptimeRunnable = new Runnable() {
            @Override
            public void run() {
                if (binding != null) {
                    VpnState state = AdShieldVpnService.getVpnStateLiveData().getValue();
                    if (state == VpnState.CONNECTED) {
                        updateUptimeText();
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
        updatePrivateDnsWarning();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        uptimeHandler.removeCallbacks(uptimeRunnable);
        if (glowPulse != null) {
            glowPulse.cancel();
            glowPulse = null;
        }
        binding = null;
    }
}
