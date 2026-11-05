package com.adshield.ui.logs;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.adshield.R;
import com.adshield.data.db.AdShieldDbHelper;
import com.adshield.data.model.DnsLogEntry;
import com.adshield.databinding.FragmentLogsBinding;

import java.util.List;

public class QueryLogFragment extends Fragment {

    private FragmentLogsBinding binding;
    private AdShieldDbHelper dbHelper;
    private QueryLogAdapter adapter;

    private String currentSearchQuery = "";
    private Boolean currentBlockedFilter = null; // null = all, true = blocked, false = allowed

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLogsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        dbHelper = AdShieldDbHelper.getInstance(context);

        setupRecyclerView();
        setupSearchAndFilters();
        setupClearButton();
        startAutoRefresh();
    }

    private void setupRecyclerView() {
        adapter = new QueryLogAdapter();
        binding.recyclerQueryLogs.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerQueryLogs.setAdapter(adapter);
    }

    private void setupSearchAndFilters() {
        binding.etSearchLogs.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().trim();
                binding.btnClearSearch.setVisibility(currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                loadLogs();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnClearSearch.setOnClickListener(v -> binding.etSearchLogs.setText(""));

        binding.chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty() || checkedIds.contains(R.id.chip_all)) {
                currentBlockedFilter = null;
            } else if (checkedIds.contains(R.id.chip_blocked)) {
                currentBlockedFilter = true;
            } else if (checkedIds.contains(R.id.chip_allowed)) {
                currentBlockedFilter = false;
            }
            loadLogs();
        });
    }

    private void setupClearButton() {
        binding.btnClearLogs.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Clear Query Logs")
                    .setMessage("Are you sure you want to delete all recorded DNS query logs?")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        dbHelper.clearLogs();
                        loadLogs();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void loadLogs() {
        if (!isAdded()) return;

        List<DnsLogEntry> logs = dbHelper.getRecentLogs(100, currentSearchQuery, currentBlockedFilter);
        if (logs.isEmpty()) {
            binding.tvEmptyLogs.setVisibility(View.VISIBLE);
            binding.recyclerQueryLogs.setVisibility(View.GONE);
        } else {
            binding.tvEmptyLogs.setVisibility(View.GONE);
            binding.recyclerQueryLogs.setVisibility(View.VISIBLE);
            adapter.setItems(logs);
        }
    }

    private void startAutoRefresh() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                loadLogs();
                // Refresh log list every 2.5 seconds if fragment is visible
                refreshHandler.postDelayed(this, 2500L);
            }
        };
        refreshHandler.post(refreshRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadLogs();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        refreshHandler.removeCallbacks(refreshRunnable);
        binding = null;
    }
}
