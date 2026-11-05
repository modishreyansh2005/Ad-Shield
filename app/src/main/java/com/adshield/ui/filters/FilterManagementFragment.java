package com.adshield.ui.filters;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.adshield.R;
import com.adshield.data.db.AdShieldDbHelper;
import com.adshield.data.model.FilterRule;
import com.adshield.databinding.FragmentFiltersBinding;
import com.adshield.dns.DomainBlocklist;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FilterManagementFragment extends Fragment implements FilterRulesAdapter.OnRuleActionListener {

    private FragmentFiltersBinding binding;
    private AdShieldDbHelper dbHelper;
    private FilterRulesAdapter adapter;
    private int currentTabIndex = 0; // 0 = Blacklist, 1 = Whitelist, 2 = Community

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFiltersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        dbHelper = AdShieldDbHelper.getInstance(context);

        setupRecyclerView();
        setupTabs();
        setupFab();
        loadTabRules();
    }

    private void setupRecyclerView() {
        adapter = new FilterRulesAdapter(this);
        binding.recyclerFilters.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerFilters.setAdapter(adapter);
    }

    private void setupTabs() {
        binding.tabLayoutFilters.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTabIndex = tab.getPosition();
                loadTabRules();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupFab() {
        binding.fabAddRule.setOnClickListener(v -> showAddRuleDialog());
    }

    private void loadTabRules() {
        if (!isAdded()) return;

        if (currentTabIndex == 0) {
            // Blacklist
            binding.tvFiltersSummary.setText("Custom blocked domains. Matches all subdomains automatically.");
            binding.fabAddRule.setVisibility(View.VISIBLE);
            binding.fabAddRule.setText("Add Blocked Domain");
            adapter.setReadOnly(false);

            List<FilterRule> rules = dbHelper.getRules(FilterRule.Type.BLACKLIST);
            updateListUi(rules);
        } else if (currentTabIndex == 1) {
            // Whitelist
            binding.tvFiltersSummary.setText("Allowed domains that bypass all ad blocking and filtering.");
            binding.fabAddRule.setVisibility(View.VISIBLE);
            binding.fabAddRule.setText("Add Whitelist Domain");
            adapter.setReadOnly(false);

            List<FilterRule> rules = dbHelper.getRules(FilterRule.Type.WHITELIST);
            updateListUi(rules);
        } else {
            // Built-in Community
            binding.tvFiltersSummary.setText("Verified community security & ad blocking definitions included offline.");
            binding.fabAddRule.setVisibility(View.GONE);
            adapter.setReadOnly(true);

            List<FilterRule> rules = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (String domain : DomainBlocklist.BUILT_IN_BLOCKED_DOMAINS) {
                rules.add(new FilterRule(0, domain, FilterRule.Type.BLACKLIST, true, now));
            }
            updateListUi(rules);
        }
    }

    private void updateListUi(List<FilterRule> rules) {
        if (rules.isEmpty()) {
            binding.tvEmptyFilters.setVisibility(View.VISIBLE);
            binding.recyclerFilters.setVisibility(View.GONE);
        } else {
            binding.tvEmptyFilters.setVisibility(View.GONE);
            binding.recyclerFilters.setVisibility(View.VISIBLE);
            adapter.setItems(rules);
        }
    }

    private void showAddRuleDialog() {
        FilterRule.Type targetType = (currentTabIndex == 0) ? FilterRule.Type.BLACKLIST : FilterRule.Type.WHITELIST;
        String title = (targetType == FilterRule.Type.BLACKLIST) ? "Add Domain to Blacklist" : "Add Domain to Whitelist";

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_rule, null);
        EditText etDomain = dialogView.findViewById(R.id.et_rule_domain);

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton("Add Rule", (dialog, which) -> {
                    String domain = etDomain.getText().toString().trim().toLowerCase(Locale.US);
                    if (domain.isEmpty() || !domain.contains(".") || domain.contains(" ")) {
                        Toast.makeText(requireContext(), "Please enter a valid domain name (e.g. ads.example.com)", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean success = dbHelper.addOrUpdateRule(domain, targetType);
                    if (success) {
                        Toast.makeText(requireContext(), "Rule added: " + domain, Toast.LENGTH_SHORT).show();
                        loadTabRules();
                    } else {
                        Toast.makeText(requireContext(), "Failed to add rule.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onRuleToggle(FilterRule rule, boolean isEnabled) {
        dbHelper.setRuleEnabled(rule.getDomain(), isEnabled);
    }

    @Override
    public void onRuleDelete(FilterRule rule) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Rule")
                .setMessage("Are you sure you want to delete '" + rule.getDomain() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbHelper.deleteRule(rule.getDomain());
                    loadTabRules();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
