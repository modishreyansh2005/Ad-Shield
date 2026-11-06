package com.adshield.ui.apps;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.adshield.data.model.AppItem;
import com.adshield.databinding.ItemAppSelectionBinding;

import java.util.ArrayList;
import java.util.List;

public class AppSelectionAdapter extends RecyclerView.Adapter<AppSelectionAdapter.AppViewHolder> {

    public interface OnAppProtectionChangeListener {
        void onProtectionChanged(AppItem item, boolean isProtected);
    }

    private final List<AppItem> items = new ArrayList<>();
    private final OnAppProtectionChangeListener listener;

    public AppSelectionAdapter(OnAppProtectionChangeListener listener) {
        this.listener = listener;
    }

    public void setItems(List<AppItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAppSelectionBinding binding = ItemAppSelectionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new AppViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class AppViewHolder extends RecyclerView.ViewHolder {

        private final ItemAppSelectionBinding binding;

        public AppViewHolder(@NonNull ItemAppSelectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(AppItem item) {
            binding.ivAppIcon.setImageDrawable(item.getIcon());
            binding.tvAppName.setText(item.getAppName());
            binding.tvPackageName.setText(item.getPackageName());

            // Clear listener before updating checked state to prevent unwanted triggers
            binding.switchProtect.setOnCheckedChangeListener(null);
            binding.switchProtect.setChecked(item.isProtected());

            updateStatusLabel(item.isProtected());

            binding.switchProtect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.setProtected(isChecked);
                updateStatusLabel(isChecked);
                if (listener != null) {
                    listener.onProtectionChanged(item, isChecked);
                }
            });
        }

        private void updateStatusLabel(boolean isProtected) {
            if (isProtected) {
                binding.tvAppStatus.setText("PROTECTED");
                binding.tvAppStatus.setTextColor(Color.parseColor("#10B981")); // Emerald Green
            } else {
                binding.tvAppStatus.setText("NOT PROTECTED");
                binding.tvAppStatus.setTextColor(Color.parseColor("#94A3B8")); // Muted Gray
            }
        }
    }
}
