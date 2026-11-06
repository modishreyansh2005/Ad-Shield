package com.adshield.ui.filters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.adshield.data.model.FilterRule;
import com.adshield.databinding.ItemFilterRuleBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FilterRulesAdapter extends RecyclerView.Adapter<FilterRulesAdapter.RuleViewHolder> {

    public interface OnRuleActionListener {
        void onRuleToggle(FilterRule rule, boolean isEnabled);
        void onRuleDelete(FilterRule rule);
    }

    private final List<FilterRule> items = new ArrayList<>();
    private final OnRuleActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private boolean isReadOnly = false;

    public FilterRulesAdapter(OnRuleActionListener listener) {
        this.listener = listener;
    }

    public void setReadOnly(boolean readOnly) {
        this.isReadOnly = readOnly;
    }

    public void setItems(List<FilterRule> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFilterRuleBinding binding = ItemFilterRuleBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new RuleViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RuleViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class RuleViewHolder extends RecyclerView.ViewHolder {

        private final ItemFilterRuleBinding binding;

        public RuleViewHolder(@NonNull ItemFilterRuleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(FilterRule rule) {
            binding.tvRuleDomain.setText(rule.getDomain());

            if (isReadOnly) {
                binding.tvRuleDate.setText("Built into AdShield");
                binding.btnDeleteRule.setVisibility(View.GONE);
                binding.switchRuleEnabled.setVisibility(View.GONE);
            } else {
                binding.tvRuleDate.setText("Added: " + dateFormat.format(new Date(rule.getCreatedAt())));
                binding.btnDeleteRule.setVisibility(View.VISIBLE);
                binding.switchRuleEnabled.setVisibility(View.VISIBLE);

                binding.switchRuleEnabled.setOnCheckedChangeListener(null);
                binding.switchRuleEnabled.setChecked(rule.isEnabled());

                binding.switchRuleEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    rule.setEnabled(isChecked);
                    if (listener != null) {
                        listener.onRuleToggle(rule, isChecked);
                    }
                });

                binding.btnDeleteRule.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onRuleDelete(rule);
                    }
                });
            }
        }
    }
}
