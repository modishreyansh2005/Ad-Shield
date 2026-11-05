package com.adshield.ui.stats;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.adshield.databinding.ItemTopDomainBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TopDomainsAdapter extends RecyclerView.Adapter<TopDomainsAdapter.TopDomainViewHolder> {

    public static class DomainCount {
        public final String domain;
        public final long count;

        public DomainCount(String domain, long count) {
            this.domain = domain;
            this.count = count;
        }
    }

    private final List<DomainCount> items = new ArrayList<>();
    private long maxCount = 1;

    public void setItems(Map<String, Long> domainMap) {
        this.items.clear();
        this.maxCount = 1;

        if (domainMap != null) {
            List<Map.Entry<String, Long>> list = new ArrayList<>(domainMap.entrySet());
            // Sort by count descending
            list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

            for (Map.Entry<String, Long> entry : list) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                }
                items.add(new DomainCount(entry.getKey(), entry.getValue()));
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TopDomainViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTopDomainBinding binding = ItemTopDomainBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new TopDomainViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TopDomainViewHolder holder, int position) {
        holder.bind(items.get(position), position + 1, maxCount);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class TopDomainViewHolder extends RecyclerView.ViewHolder {

        private final ItemTopDomainBinding binding;

        public TopDomainViewHolder(@NonNull ItemTopDomainBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(DomainCount item, int rank, long max) {
            binding.tvTopDomainRank.setText(String.format(Locale.US, "%d.", rank));
            binding.tvTopDomainName.setText(item.domain);
            binding.tvTopDomainCount.setText(String.format(Locale.US, "%,d", item.count));

            int progress = (int) Math.min(100, (item.count * 100) / Math.max(1, max));
            binding.progressTopDomain.setProgress(progress);
        }
    }
}
