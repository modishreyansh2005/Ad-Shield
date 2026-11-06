package com.adshield.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.adshield.R;
import com.adshield.data.PreferencesManager;
import com.adshield.databinding.DialogAppStatusBinding;

import java.util.Locale;

public class AppStatusDialog extends DialogFragment {

    public enum AppTarget {
        YOUTUBE,
        SPOTIFY
    }

    private static final String ARG_TARGET = "arg_target";

    public static void show(FragmentManager fm, AppTarget target) {
        AppStatusDialog dialog = new AppStatusDialog();
        Bundle args = new Bundle();
        args.putString(ARG_TARGET, target.name());
        dialog.setArguments(args);
        dialog.show(fm, "AppStatusDialog");
    }

    private DialogAppStatusBinding binding;

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogAppStatusBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        PreferencesManager prefs = new PreferencesManager(context);

        String targetStr = (getArguments() != null) ? getArguments().getString(ARG_TARGET) : AppTarget.YOUTUBE.name();
        AppTarget target = AppTarget.valueOf(targetStr);

        if (target == AppTarget.YOUTUBE) {
            binding.dialogAppIcon.setImageResource(R.drawable.ic_youtube);
            binding.dialogAppTitle.setText("YouTube");
            long blocked = prefs.getYouTubeBlockedCount();
            binding.dialogAppBlockedCount.setText(String.format(Locale.US, "%,d blocked", blocked));
            binding.dialogAppDescription.setText(
                    "AdShield blocks YouTube's ad trackers and mutes video ads in the YouTube app.\n\n" +
                    "Video ads still appear in the YouTube app, because they come from the same place as the videos. " +
                    "To watch with no ads at all, use \"Watch without ads in AdShield\" below."
            );
        } else {
            binding.dialogAppIcon.setImageResource(R.drawable.ic_spotify);
            binding.dialogAppTitle.setText("Spotify");
            long blocked = prefs.getSpotifyBlockedCount();
            binding.dialogAppBlockedCount.setText(String.format(Locale.US, "%,d blocked", blocked));
            binding.dialogAppDescription.setText(
                    "AdShield blocks Spotify's ad trackers and mutes audio ads, even when Spotify plays in the background.\n\n" +
                    "For muting to work, open Spotify → Settings and turn on Device Broadcast Status. " +
                    "Your music is never blocked."
            );
        }

        binding.dialogBtnLaunchHub.setOnClickListener(v -> {
            String targetExtra = com.adshield.ui.player.CyberPlayerActivity.TARGET_YOUTUBE;
            if (target == AppTarget.SPOTIFY) {
                targetExtra = com.adshield.ui.player.CyberPlayerActivity.TARGET_SPOTIFY;
            }
            android.content.Intent intent = new android.content.Intent(requireContext(), com.adshield.ui.player.CyberPlayerActivity.class);
            intent.putExtra(com.adshield.ui.player.CyberPlayerActivity.EXTRA_TARGET, targetExtra);
            startActivity(intent);
            dismiss();
        });

        binding.dialogBtnClose.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
