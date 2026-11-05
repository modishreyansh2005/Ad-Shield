package com.adshield.ui.logs;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.adshield.R;
import com.adshield.data.model.DnsLogEntry;
import com.adshield.databinding.ItemDnsLogBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QueryLogAdapter extends RecyclerView.Adapter<QueryLogAdapter.LogViewHolder> {

    private final List<DnsLogEntry> items = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public void setItems(List<DnsLogEntry> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDnsLogBinding binding = ItemDnsLogBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new LogViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class LogViewHolder extends RecyclerView.ViewHolder {

        private final ItemDnsLogBinding binding;

        public LogViewHolder(@NonNull ItemDnsLogBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(DnsLogEntry entry) {
            binding.tvDomain.setText(entry.getDomain());
            binding.tvQtypeBadge.setText(entry.getQtype() != null ? entry.getQtype() : "A");

            // Format timestamp
            binding.tvTimestamp.setText(timeFormat.format(new Date(entry.getTimestamp())));

            // Status indicator & coloring
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);

            if (entry.isBlocked()) {
                dot.setColor(Color.parseColor("#EF4444")); // Red
                binding.viewStatusIndicator.setBackground(dot);

                String appInfo = (entry.getAppName() != null && !entry.getAppName().isEmpty())
                        ? entry.getAppName() + " • "
                        : "";
                binding.tvAppName.setText(appInfo + "BLOCKED (0.0.0.0)");
                binding.tvAppName.setTextColor(Color.parseColor("#EF4444"));
            } else {
                dot.setColor(Color.parseColor("#10B981")); // Green
                binding.viewStatusIndicator.setBackground(dot);

                String ipInfo = (entry.getResolvedIp() != null && !entry.getResolvedIp().isEmpty())
                        ? " • " + entry.getResolvedIp()
                        : "";
                String appInfo = (entry.getAppName() != null && !entry.getAppName().isEmpty())
                        ? entry.getAppName()
                        : "Network";
                binding.tvAppName.setText(appInfo + ipInfo);
                binding.tvAppName.setTextColor(binding.getRoot().getContext().getColor(R.color.text_secondary_light));
            }
        }
    }
}
