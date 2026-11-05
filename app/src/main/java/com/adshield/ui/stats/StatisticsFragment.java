package com.adshield.ui.stats;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.adshield.data.PreferencesManager;
import com.adshield.data.db.AdShieldDbHelper;
import com.adshield.databinding.FragmentStatisticsBinding;

import java.util.Locale;
import java.util.Map;

public class StatisticsFragment extends Fragment {

    private FragmentStatisticsBinding binding;
    private PreferencesManager prefsManager;
    private AdShieldDbHelper dbHelper;
    private TopDomainsAdapter topDomainsAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentStatisticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        prefsManager = new PreferencesManager(context);
        dbHelper = AdShieldDbHelper.getInstance(context);

        setupRecyclerView();
        loadStatistics();
    }

    private void setupRecyclerView() {
        topDomainsAdapter = new TopDomainsAdapter();
        binding.recyclerTopDomains.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerTopDomains.setAdapter(topDomainsAdapter);
    }

    private void loadStatistics() {
        if (!isAdded()) return;

        long blocked = prefsManager.getBlockedRequestsCount();
        long total = prefsManager.getTotalQueriesCount();
        long allowed = Math.max(0, total - blocked);

        float percentage = (total > 0) ? ((float) blocked / total * 100f) : 0f;
        binding.tvStatsBlockRate.setText(String.format(Locale.US, "%.1f%%", percentage));
        binding.progressRatioBar.setProgress((int) percentage);

        binding.tvStatsBlocked.setText(String.format(Locale.US, "%,d", blocked));
        binding.tvStatsAllowed.setText(String.format(Locale.US, "%,d", allowed));
        binding.tvStatsTotal.setText(String.format(Locale.US, "%,d", total));

        // Top Blocked Domains
        Map<String, Long> topMap = dbHelper.getTopBlockedDomains(10);
        if (topMap.isEmpty()) {
            binding.tvEmptyTopDomains.setVisibility(View.VISIBLE);
            binding.recyclerTopDomains.setVisibility(View.GONE);
        } else {
            binding.tvEmptyTopDomains.setVisibility(View.GONE);
            binding.recyclerTopDomains.setVisibility(View.VISIBLE);
            topDomainsAdapter.setItems(topMap);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadStatistics();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
