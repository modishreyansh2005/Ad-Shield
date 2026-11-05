package com.adshield.ui.apps;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
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
import com.adshield.data.PreferencesManager;
import com.adshield.data.model.AppItem;
import com.adshield.databinding.FragmentAppsBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppSelectionFragment extends Fragment {

    private static final Set<String> HIGHLIGHTED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.youtube",
            "com.spotify.music",
            "in.startv.hotstar",
            "com.jio.media.ondemand",
            "com.instagram.android",
            "com.facebook.katana",
            "com.android.chrome"
    ));

    private FragmentAppsBinding binding;
    private PreferencesManager prefsManager;
    private AppSelectionAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<AppItem> allApps = new ArrayList<>();
    private String currentSearch = "";
    private int currentFilterId = R.id.chip_all_apps;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAppsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        prefsManager = new PreferencesManager(context);

        setupRecyclerView();
        setupSearchAndFilters();
        loadInstalledApps();
    }

    private void setupRecyclerView() {
        adapter = new AppSelectionAdapter((item, isProtected) -> {
            // If protected -> excluded = false; If bypassed -> excluded = true
            prefsManager.setPackageExcluded(item.getPackageName(), !isProtected);
        });
        binding.recyclerApps.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerApps.setAdapter(adapter);
    }

    private void setupSearchAndFilters() {
        binding.etSearchApps.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearch = s.toString().trim().toLowerCase(Locale.getDefault());
                binding.btnClearAppSearch.setVisibility(currentSearch.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnClearAppSearch.setOnClickListener(v -> binding.etSearchApps.setText(""));

        binding.chipGroupAppFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                currentFilterId = checkedIds.get(0);
            } else {
                currentFilterId = R.id.chip_all_apps;
            }
            applyFilter();
        });
    }

    private void loadInstalledApps() {
        binding.progressLoadingApps.setVisibility(View.VISIBLE);
        binding.recyclerApps.setVisibility(View.GONE);

        executor.execute(() -> {
            PackageManager pm = requireContext().getPackageManager();
            Set<String> excludedPackages = prefsManager.getExcludedPackages();

            // Find all launcher apps
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);

            Set<String> seenPackages = new HashSet<>();
            List<AppItem> loaded = new ArrayList<>();

            for (ResolveInfo info : resolveInfos) {
                if (info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (pkg == null || seenPackages.contains(pkg) || pkg.equals(requireContext().getPackageName())) {
                    continue;
                }
                seenPackages.add(pkg);

                String name = info.loadLabel(pm).toString();
                Drawable icon = info.loadIcon(pm);
                boolean isProtected = !excludedPackages.contains(pkg);
                boolean isHighlighted = HIGHLIGHTED_PACKAGES.contains(pkg);

                loaded.add(new AppItem(pkg, name, icon, isProtected, isHighlighted));
            }

            // Also check if any highlighted apps are installed but without standard launcher
            for (String highlightPkg : HIGHLIGHTED_PACKAGES) {
                if (!seenPackages.contains(highlightPkg)) {
                    try {
                        ApplicationInfo appInfo = pm.getApplicationInfo(highlightPkg, 0);
                        String name = pm.getApplicationLabel(appInfo).toString();
                        Drawable icon = pm.getApplicationIcon(appInfo);
                        boolean isProtected = !excludedPackages.contains(highlightPkg);
                        loaded.add(new AppItem(highlightPkg, name, icon, isProtected, true));
                        seenPackages.add(highlightPkg);
                    } catch (PackageManager.NameNotFoundException ignored) {}
                }
            }

            // Sort: highlighted apps first, then alphabetically by app name
            loaded.sort((a, b) -> {
                if (a.isHighlighted() && !b.isHighlighted()) return -1;
                if (!a.isHighlighted() && b.isHighlighted()) return 1;
                return a.getAppName().compareToIgnoreCase(b.getAppName());
            });

            mainHandler.post(() -> {
                if (!isAdded()) return;
                allApps.clear();
                allApps.addAll(loaded);
                binding.progressLoadingApps.setVisibility(View.GONE);
                binding.recyclerApps.setVisibility(View.VISIBLE);
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        List<AppItem> filtered = new ArrayList<>();
        for (AppItem app : allApps) {
            // Apply search match
            boolean matchesSearch = currentSearch.isEmpty()
                    || app.getAppName().toLowerCase(Locale.getDefault()).contains(currentSearch)
                    || app.getPackageName().toLowerCase(Locale.getDefault()).contains(currentSearch);

            if (!matchesSearch) continue;

            // Apply chip filter
            if (currentFilterId == R.id.chip_protected_apps && !app.isProtected()) {
                continue;
            } else if (currentFilterId == R.id.chip_excluded_apps && app.isProtected()) {
                continue;
            }

            filtered.add(app);
        }
        adapter.setItems(filtered);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
