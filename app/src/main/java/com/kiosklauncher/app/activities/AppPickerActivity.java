package com.kiosklauncher.app.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kiosklauncher.app.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Two-level App + Shortcut Picker:
 *  Level 1 → list semua apps
 *  Level 2 → list semua Activity/Shortcut di dalam app yang dipilih
 */
public class AppPickerActivity extends AppCompatActivity {

    // ─── Mode picker ────────────────────────────────────────────────────────
    private static final int MODE_APPS      = 0;
    private static final int MODE_SHORTCUTS = 1;

    private int currentMode = MODE_APPS;

    private RecyclerView recyclerView;
    private EditText searchBox;
    private TextView tvTitle, tvBreadcrumb;

    // App list
    private final List<AppInfo> allApps      = new ArrayList<>();
    private final List<AppInfo> filteredApps = new ArrayList<>();

    // Shortcut list for selected app
    private AppInfo selectedApp;
    private final List<ShortcutItem> allShortcuts      = new ArrayList<>();
    private final List<ShortcutItem> filteredShortcuts = new ArrayList<>();

    private AppAdapter     appAdapter;
    private ShortcutAdapter shortcutAdapter;

    // ────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);

        tvTitle      = findViewById(R.id.tv_picker_title);
        tvBreadcrumb = findViewById(R.id.tv_breadcrumb);
        recyclerView = findViewById(R.id.recycler_apps);
        searchBox    = findViewById(R.id.et_search);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {
                filterCurrent(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadApps();
        showAppList();
    }

    @Override
    public void onBackPressed() {
        if (currentMode == MODE_SHORTCUTS) {
            showAppList();   // kembali ke list app
        } else {
            super.onBackPressed();
        }
    }

    // ─────────────────────────── LEVEL 1: APPS ──────────────────────────────

    private void loadApps() {
        allApps.clear();
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(mainIntent, 0);

        for (ResolveInfo ri : ris) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            allApps.add(new AppInfo(
                    ri.loadLabel(pm).toString(),
                    pkg,
                    ri.activityInfo.name,
                    ri.loadIcon(pm)
            ));
        }
        Collections.sort(allApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
    }

    private void showAppList() {
        currentMode = MODE_APPS;
        searchBox.setText("");
        tvTitle.setText("📱 Pilih Aplikasi");
        tvBreadcrumb.setVisibility(View.GONE);

        filteredApps.clear();
        filteredApps.addAll(allApps);

        appAdapter = new AppAdapter(filteredApps, app -> {
            selectedApp = app;
            showShortcutList(app);
        });
        recyclerView.setAdapter(appAdapter);
    }

    // ─────────────────────────── LEVEL 2: SHORTCUTS ─────────────────────────

    private void showShortcutList(AppInfo app) {
        currentMode = MODE_SHORTCUTS;
        searchBox.setText("");
        tvTitle.setText("🔗 Pilih Halaman / Shortcut");
        tvBreadcrumb.setVisibility(View.VISIBLE);
        tvBreadcrumb.setText("◀ " + app.label);
        tvBreadcrumb.setOnClickListener(v -> showAppList());

        allShortcuts.clear();

        PackageManager pm = getPackageManager();

        // ── 1. Default launcher intent (ikon utama) ─────────────────────────
        Intent defaultIntent = pm.getLaunchIntentForPackage(app.packageName);
        if (defaultIntent != null) {
            allShortcuts.add(new ShortcutItem(
                    "🏠 " + app.label + " (Halaman Utama)",
                    app.packageName,
                    null,         // no specific activity — use launch intent
                    null,         // no shortcut id
                    app.icon,
                    ShortcutItem.TYPE_MAIN
            ));
        }

        // ── 2. Semua Activity yang bisa diluncurkan ─────────────────────────
        Intent queryIntent = new Intent(Intent.ACTION_MAIN);
        queryIntent.setPackage(app.packageName);
        List<ResolveInfo> activities = pm.queryIntentActivities(queryIntent, 0);

        for (ResolveInfo ri : activities) {
            String actName = ri.activityInfo.name;
            String label   = ri.loadLabel(pm).toString();
            Drawable icon  = ri.loadIcon(pm);

            // Jangan duplikat halaman utama
            if (actName.equals(app.mainActivity)) continue;

            // Buat label yang lebih mudah dibaca
            String shortName = actName.contains(".") ?
                    actName.substring(actName.lastIndexOf('.') + 1) : actName;

            allShortcuts.add(new ShortcutItem(
                    "📄 " + label + " (" + shortName + ")",
                    app.packageName,
                    actName,
                    null,
                    icon,
                    ShortcutItem.TYPE_ACTIVITY
            ));
        }

        // ── 3. App Shortcuts (Android 7.1+ / API 25+) ───────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            loadAppShortcuts(app, pm);
        }

        // ── 4. Semua Activity publik via packageInfo ─────────────────────────
        try {
            android.content.pm.PackageInfo pkgInfo = pm.getPackageInfo(
                    app.packageName,
                    PackageManager.GET_ACTIVITIES
            );
            if (pkgInfo.activities != null) {
                for (ActivityInfo ai : pkgInfo.activities) {
                    if (!ai.exported) continue; // skip non-exported
                    String actName = ai.name;

                    // Cek sudah ada di list?
                    boolean exists = false;
                    for (ShortcutItem s : allShortcuts) {
                        if (actName.equals(s.activityName)) { exists = true; break; }
                    }
                    if (exists) continue;
                    if (actName.equals(app.mainActivity)) continue;

                    String shortName = actName.contains(".") ?
                            actName.substring(actName.lastIndexOf('.') + 1) : actName;

                    Drawable icon;
                    try { icon = pm.getActivityIcon(new ComponentName(app.packageName, actName)); }
                    catch (Exception e) { icon = app.icon; }

                    allShortcuts.add(new ShortcutItem(
                            "🗂️ " + shortName,
                            app.packageName,
                            actName,
                            null,
                            icon,
                            ShortcutItem.TYPE_ACTIVITY
                    ));
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {}

        if (allShortcuts.isEmpty()) {
            Toast.makeText(this, "Tidak ada shortcut/halaman ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }

        filteredShortcuts.clear();
        filteredShortcuts.addAll(allShortcuts);

        shortcutAdapter = new ShortcutAdapter(filteredShortcuts, this::selectShortcut);
        recyclerView.setAdapter(shortcutAdapter);
    }

    /** Load dynamic & static App Shortcuts via LauncherApps */
    private void loadAppShortcuts(AppInfo app, PackageManager pm) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return;
        try {
            android.content.pm.LauncherApps launcherApps =
                    (android.content.pm.LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (launcherApps == null) return;

            android.content.pm.LauncherApps.ShortcutQuery query =
                    new android.content.pm.LauncherApps.ShortcutQuery();
            query.setQueryFlags(
                    android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST |
                    android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC |
                    android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            );
            query.setPackage(app.packageName);

            android.os.UserHandle user = android.os.Process.myUserHandle();
            List<ShortcutInfo> shortcuts = launcherApps.getShortcuts(query, user);

            if (shortcuts == null) return;
            for (ShortcutInfo sc : shortcuts) {
                if (!sc.isEnabled()) continue;
                String label = sc.getLongLabel() != null ?
                        sc.getLongLabel().toString() :
                        (sc.getShortLabel() != null ? sc.getShortLabel().toString() : sc.getId());

                Drawable icon;
                try {
                    icon = launcherApps.getShortcutIconDrawable(sc,
                            getResources().getDisplayMetrics().densityDpi);
                } catch (Exception e) { icon = app.icon; }
                if (icon == null) icon = app.icon;

                allShortcuts.add(new ShortcutItem(
                        "⚡ " + label,
                        app.packageName,
                        null,
                        sc.getId(),
                        icon,
                        ShortcutItem.TYPE_SHORTCUT
                ));
            }
        } catch (Exception ignored) {
            // Shortcut query requires launcher role or permission — silently skip
        }
    }

    // ─────────────────────────── SELECT ─────────────────────────────────────

    private void selectShortcut(ShortcutItem item) {
        SharedPreferences prefs = getSharedPreferences(
                LauncherActivity.PREFS_NAME, Context.MODE_PRIVATE);

        prefs.edit()
                .putString(LauncherActivity.KEY_KIOSK_APP,         item.packageName)
                .putString(LauncherActivity.KEY_KIOSK_ACTIVITY,    item.activityName != null ? item.activityName : "")
                .putString(LauncherActivity.KEY_KIOSK_SHORTCUT_ID, item.shortcutId   != null ? item.shortcutId   : "")
                .putInt   (LauncherActivity.KEY_KIOSK_TYPE,        item.type)
                .putBoolean(LauncherActivity.KEY_KIOSK_ENABLED,    true)
                .apply();

        String msg = "✅ Dipilih: " + item.label;
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        finish();
    }

    // ─────────────────────────── FILTER ─────────────────────────────────────

    private void filterCurrent(String query) {
        String q = query.toLowerCase().trim();
        if (currentMode == MODE_APPS) {
            filteredApps.clear();
            for (AppInfo a : allApps) {
                if (a.label.toLowerCase().contains(q) || a.packageName.toLowerCase().contains(q))
                    filteredApps.add(a);
            }
            if (appAdapter != null) appAdapter.notifyDataSetChanged();
        } else {
            filteredShortcuts.clear();
            for (ShortcutItem s : allShortcuts) {
                if (s.label.toLowerCase().contains(q) || s.packageName.toLowerCase().contains(q))
                    filteredShortcuts.add(s);
            }
            if (shortcutAdapter != null) shortcutAdapter.notifyDataSetChanged();
        }
    }

    // ══════════════════════════ DATA CLASSES ════════════════════════════════

    public static class AppInfo {
        public String   label, packageName, mainActivity;
        public Drawable icon;
        AppInfo(String label, String packageName, String mainActivity, Drawable icon) {
            this.label = label; this.packageName = packageName;
            this.mainActivity = mainActivity; this.icon = icon;
        }
    }

    public static class ShortcutItem {
        public static final int TYPE_MAIN     = 0;
        public static final int TYPE_ACTIVITY = 1;
        public static final int TYPE_SHORTCUT = 2;

        public String   label, packageName, activityName, shortcutId;
        public Drawable icon;
        public int      type;

        ShortcutItem(String label, String packageName, String activityName,
                     String shortcutId, Drawable icon, int type) {
            this.label = label; this.packageName = packageName;
            this.activityName = activityName; this.shortcutId = shortcutId;
            this.icon = icon; this.type = type;
        }
    }

    // ══════════════════════════ ADAPTERS ════════════════════════════════════

    static class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {
        private final List<AppInfo>  list;
        private final OnClick        onClick;
        interface OnClick { void on(AppInfo a); }

        AppAdapter(List<AppInfo> list, OnClick onClick) {
            this.list = list; this.onClick = onClick;
        }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_app, p, false));
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            AppInfo a = list.get(pos);
            h.tvName.setText(a.label);
            h.tvSub.setText(a.packageName);
            h.icon.setImageDrawable(a.icon);
            h.tvArrow.setText("▶");
            h.itemView.setOnClickListener(v -> onClick.on(a));
        }
        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView icon; TextView tvName, tvSub, tvArrow;
            VH(View v) {
                super(v);
                icon   = v.findViewById(R.id.iv_app_icon);
                tvName = v.findViewById(R.id.tv_app_name);
                tvSub  = v.findViewById(R.id.tv_app_pkg);
                tvArrow= v.findViewById(R.id.tv_app_arrow);
            }
        }
    }

    static class ShortcutAdapter extends RecyclerView.Adapter<ShortcutAdapter.VH> {
        private final List<ShortcutItem> list;
        private final OnClick            onClick;
        interface OnClick { void on(ShortcutItem s); }

        ShortcutAdapter(List<ShortcutItem> list, OnClick onClick) {
            this.list = list; this.onClick = onClick;
        }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_app, p, false));
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            ShortcutItem s = list.get(pos);
            h.tvName.setText(s.label);
            h.tvSub.setText(s.packageName +
                    (s.activityName != null ? "\n" + s.activityName : "") +
                    (s.shortcutId   != null ? "\nID: " + s.shortcutId : ""));
            h.icon.setImageDrawable(s.icon);
            // Badge by type
            switch (s.type) {
                case ShortcutItem.TYPE_MAIN:     h.tvArrow.setText("🏠"); break;
                case ShortcutItem.TYPE_SHORTCUT: h.tvArrow.setText("⚡"); break;
                default:                         h.tvArrow.setText("✓");  break;
            }
            h.itemView.setOnClickListener(v -> onClick.on(s));
        }
        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView icon; TextView tvName, tvSub, tvArrow;
            VH(View v) {
                super(v);
                icon   = v.findViewById(R.id.iv_app_icon);
                tvName = v.findViewById(R.id.tv_app_name);
                tvSub  = v.findViewById(R.id.tv_app_pkg);
                tvArrow= v.findViewById(R.id.tv_app_arrow);
            }
        }
    }
}
