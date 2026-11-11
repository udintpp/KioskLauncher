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
 * Two-level App + Shortcut Picker
 *
 * Level 1 → semua installed apps
 * Level 2 → SEMUA activity (exported & non-exported) + App Shortcuts + Deep Links
 *            + Manual Entry input
 */
public class AppPickerActivity extends AppCompatActivity {

    private static final int MODE_APPS      = 0;
    private static final int MODE_SHORTCUTS = 1;

    private int     currentMode = MODE_APPS;
    private AppInfo selectedApp = null;

    private RecyclerView    recyclerView;
    private EditText        searchBox;
    private TextView        tvTitle, tvBreadcrumb;
    private View            cardManual;
    private EditText        etManualActivity;
    private View            btnManualLaunch;

    private final List<AppInfo>      allApps           = new ArrayList<>();
    private final List<AppInfo>      filteredApps      = new ArrayList<>();
    private final List<ShortcutItem> allShortcuts      = new ArrayList<>();
    private final List<ShortcutItem> filteredShortcuts = new ArrayList<>();

    private AppAdapter      appAdapter;
    private ShortcutAdapter shortcutAdapter;

    // ────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);

        tvTitle         = findViewById(R.id.tv_picker_title);
        tvBreadcrumb    = findViewById(R.id.tv_breadcrumb);
        recyclerView    = findViewById(R.id.recycler_apps);
        searchBox       = findViewById(R.id.et_search);
        cardManual      = findViewById(R.id.card_manual_entry);
        etManualActivity= findViewById(R.id.et_manual_activity);
        btnManualLaunch = findViewById(R.id.btn_manual_select);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) { filterCurrent(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        if (btnManualLaunch != null) {
            btnManualLaunch.setOnClickListener(v -> selectManualActivity());
        }

        loadApps();
        showAppList();
    }

    @Override
    public void onBackPressed() {
        if (currentMode == MODE_SHORTCUTS) showAppList();
        else super.onBackPressed();
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
                    ri.loadLabel(pm).toString(), pkg,
                    ri.activityInfo.name, ri.loadIcon(pm)));
        }
        Collections.sort(allApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
    }

    private void showAppList() {
        currentMode = MODE_APPS;
        searchBox.setText("");
        tvTitle.setText("📱 Pilih Aplikasi");
        tvBreadcrumb.setVisibility(View.GONE);
        if (cardManual != null) cardManual.setVisibility(View.GONE);

        filteredApps.clear();
        filteredApps.addAll(allApps);
        appAdapter = new AppAdapter(filteredApps, app -> { selectedApp = app; showShortcutList(app); });
        recyclerView.setAdapter(appAdapter);
    }

    // ─────────────────────────── LEVEL 2: SHORTCUTS ─────────────────────────

    private void showShortcutList(AppInfo app) {
        currentMode = MODE_SHORTCUTS;
        searchBox.setText("");
        tvTitle.setText("🔗 " + app.label);
        tvBreadcrumb.setVisibility(View.VISIBLE);
        tvBreadcrumb.setText("◀ Kembali ke daftar app");
        tvBreadcrumb.setOnClickListener(v -> showAppList());

        // Tampilkan manual entry card
        if (cardManual != null) {
            cardManual.setVisibility(View.VISIBLE);
            if (etManualActivity != null) {
                etManualActivity.setHint("com.example.app/.SomeActivity  atau  deeplink://uri");
            }
        }

        allShortcuts.clear();
        PackageManager pm = getPackageManager();

        // ── 1. Halaman Utama ────────────────────────────────────────────────
        allShortcuts.add(new ShortcutItem(
                "🏠 Halaman Utama (default)",
                "Buka tampilan utama aplikasi",
                app.packageName, null, null,
                app.icon, ShortcutItem.TYPE_MAIN, true));

        // ── 2. App Shortcuts (dynamic/static/pinned) ─────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            loadAppShortcuts(app, pm);
        }

        // ── 3. SEMUA Activity via PackageInfo (exported & non-exported) ──────
        try {
            android.content.pm.PackageInfo pkgInfo = pm.getPackageInfo(
                    app.packageName, PackageManager.GET_ACTIVITIES | PackageManager.GET_INTENT_FILTERS);
            if (pkgInfo.activities != null) {
                for (ActivityInfo ai : pkgInfo.activities) {
                    if (ai.name.equals(app.mainActivity)) continue;

                    boolean exported = ai.exported;
                    String shortName = ai.name.contains(".")
                            ? ai.name.substring(ai.name.lastIndexOf('.') + 1)
                            : ai.name;

                    // Coba baca label
                    String label = "";
                    try {
                        CharSequence lbl = ai.loadLabel(pm);
                        label = (lbl != null && !lbl.toString().isEmpty()) ? lbl.toString() : shortName;
                    } catch (Exception e) { label = shortName; }

                    // Icon
                    Drawable icon;
                    try { icon = pm.getActivityIcon(new ComponentName(app.packageName, ai.name)); }
                    catch (Exception e) { icon = app.icon; }

                    // Subtitle: nama class + exported status
                    String subtitle = ai.name;
                    if (!exported) subtitle += "\n⚠️ Non-exported (mungkin perlu root/adb)";

                    String emoji = exported ? "📄" : "🔒";
                    allShortcuts.add(new ShortcutItem(
                            emoji + " " + label,
                            subtitle,
                            app.packageName,
                            ai.name,
                            null,
                            icon,
                            ShortcutItem.TYPE_ACTIVITY,
                            exported));
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {}

        // ── 4. Intent-based Actions (Deep Links) ─────────────────────────────
        loadDeepLinks(app, pm);

        if (allShortcuts.size() <= 1) {
            Toast.makeText(this,
                    "ℹ️ App ini tidak mengekspos activity lain.\nGunakan Manual Entry di bawah.",
                    Toast.LENGTH_LONG).show();
        }

        filteredShortcuts.clear();
        filteredShortcuts.addAll(allShortcuts);
        shortcutAdapter = new ShortcutAdapter(filteredShortcuts, this::selectShortcut);
        recyclerView.setAdapter(shortcutAdapter);
    }

    /** Load App Shortcuts (require launcher role to get list; will work after set as default) */
    private void loadAppShortcuts(AppInfo app, PackageManager pm) {
        try {
            android.content.pm.LauncherApps launcherApps =
                    (android.content.pm.LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (launcherApps == null) return;

            android.content.pm.LauncherApps.ShortcutQuery q =
                    new android.content.pm.LauncherApps.ShortcutQuery();
            q.setQueryFlags(
                    android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST |
                    android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC |
                    android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
            q.setPackage(app.packageName);

            List<ShortcutInfo> shortcuts = launcherApps.getShortcuts(q, android.os.Process.myUserHandle());
            if (shortcuts == null) return;

            for (ShortcutInfo sc : shortcuts) {
                if (!sc.isEnabled()) continue;
                String lbl = sc.getLongLabel() != null ? sc.getLongLabel().toString()
                        : (sc.getShortLabel() != null ? sc.getShortLabel().toString() : sc.getId());
                Drawable icon;
                try { icon = launcherApps.getShortcutIconDrawable(sc,
                        getResources().getDisplayMetrics().densityDpi); }
                catch (Exception e) { icon = app.icon; }
                if (icon == null) icon = app.icon;

                allShortcuts.add(new ShortcutItem(
                        "⚡ " + lbl,
                        "App Shortcut ID: " + sc.getId(),
                        app.packageName, null, sc.getId(),
                        icon, ShortcutItem.TYPE_SHORTCUT, true));
            }
        } catch (Exception e) {
            // Jika belum jadi default launcher, tambahkan info placeholder
            allShortcuts.add(new ShortcutItem(
                    "ℹ️ App Shortcuts belum tersedia",
                    "Set Kiosk Launcher sebagai Default Launcher dulu\nlalu buka kembali untuk melihat shortcuts",
                    app.packageName, null, null,
                    app.icon, ShortcutItem.TYPE_INFO, false));
        }
    }

    /** Try to find deep link / URI intents for this package */
    private void loadDeepLinks(AppInfo app, PackageManager pm) {
        try {
            // Query VIEW intent untuk mencari deep link handlers
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setPackage(app.packageName);
            List<ResolveInfo> deepLinks = pm.queryIntentActivities(viewIntent, 0);
            for (ResolveInfo ri : deepLinks) {
                String actName = ri.activityInfo.name;
                boolean alreadyAdded = false;
                for (ShortcutItem s : allShortcuts) {
                    if (actName.equals(s.activityName)) { alreadyAdded = true; break; }
                }
                if (alreadyAdded) continue;
                if (actName.equals(app.mainActivity)) continue;

                String label = ri.loadLabel(pm).toString();
                allShortcuts.add(new ShortcutItem(
                        "🔗 " + label + " (Deep Link)",
                        actName + "\nMenerima ACTION_VIEW / URI",
                        app.packageName, actName, null,
                        ri.loadIcon(pm), ShortcutItem.TYPE_ACTIVITY, true));
            }

            // Query SEND intent
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setPackage(app.packageName);
            List<ResolveInfo> sendLinks = pm.queryIntentActivities(sendIntent, 0);
            for (ResolveInfo ri : sendLinks) {
                String actName = ri.activityInfo.name;
                boolean alreadyAdded = false;
                for (ShortcutItem s : allShortcuts) {
                    if (actName.equals(s.activityName)) { alreadyAdded = true; break; }
                }
                if (alreadyAdded) continue;
                String label = ri.loadLabel(pm).toString();
                allShortcuts.add(new ShortcutItem(
                        "📤 " + label + " (Share Target)",
                        actName,
                        app.packageName, actName, null,
                        ri.loadIcon(pm), ShortcutItem.TYPE_ACTIVITY, true));
            }
        } catch (Exception ignored) {}
    }

    // ─────────────────────────── SELECT ─────────────────────────────────────

    private void selectShortcut(ShortcutItem item) {
        if (item.type == ShortcutItem.TYPE_INFO) {
            Toast.makeText(this,
                    "Set Kiosk Launcher sebagai Default Launcher dulu!", Toast.LENGTH_LONG).show();
            return;
        }
        saveAndFinish(item);
    }

    private void selectManualActivity() {
        if (selectedApp == null || etManualActivity == null) return;
        String input = etManualActivity.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Masukkan nama activity atau URI!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Tentukan tipe: jika ada "://" → deep link / URI, selain itu → activity name
        int type;
        String activityName = null;
        String shortcutId   = null;

        if (input.contains("://")) {
            // Deep link URI → simpan sebagai activity name, nanti launch via Intent.ACTION_VIEW
            activityName = input;
            type = ShortcutItem.TYPE_DEEPLINK;
        } else {
            // Activity class name — tambah package jika belum ada
            if (!input.contains(".")) {
                activityName = selectedApp.packageName + "." + input;
            } else if (input.startsWith(".")) {
                activityName = selectedApp.packageName + input;
            } else {
                activityName = input;
            }
            type = ShortcutItem.TYPE_ACTIVITY;
        }

        ShortcutItem item = new ShortcutItem(
                "✏️ " + input, "Manual Entry",
                selectedApp.packageName, activityName, shortcutId,
                selectedApp.icon, type, true);
        saveAndFinish(item);
    }

    private void saveAndFinish(ShortcutItem item) {
        SharedPreferences prefs = getSharedPreferences(
                LauncherActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(LauncherActivity.KEY_KIOSK_APP,         item.packageName)
                .putString(LauncherActivity.KEY_KIOSK_ACTIVITY,    item.activityName  != null ? item.activityName  : "")
                .putString(LauncherActivity.KEY_KIOSK_SHORTCUT_ID, item.shortcutId    != null ? item.shortcutId    : "")
                .putInt   (LauncherActivity.KEY_KIOSK_TYPE,        item.type)
                .putBoolean(LauncherActivity.KEY_KIOSK_ENABLED,    true)
                .apply();

        Toast.makeText(this, "✅ Dipilih: " + item.label, Toast.LENGTH_SHORT).show();
        finish();
    }

    // ─────────────────────────── FILTER ─────────────────────────────────────

    private void filterCurrent(String query) {
        String q = query.toLowerCase().trim();
        if (currentMode == MODE_APPS) {
            filteredApps.clear();
            for (AppInfo a : allApps)
                if (a.label.toLowerCase().contains(q) || a.packageName.toLowerCase().contains(q))
                    filteredApps.add(a);
            if (appAdapter != null) appAdapter.notifyDataSetChanged();
        } else {
            filteredShortcuts.clear();
            for (ShortcutItem s : allShortcuts)
                if (s.label.toLowerCase().contains(q) || s.subtitle.toLowerCase().contains(q))
                    filteredShortcuts.add(s);
            if (shortcutAdapter != null) shortcutAdapter.notifyDataSetChanged();
        }
    }

    // ══════════════════════════ DATA CLASSES ════════════════════════════════

    public static class AppInfo {
        public String label, packageName, mainActivity;
        public Drawable icon;
        AppInfo(String l, String p, String m, Drawable i) {
            label=l; packageName=p; mainActivity=m; icon=i;
        }
    }

    public static class ShortcutItem {
        public static final int TYPE_MAIN     = 0;
        public static final int TYPE_ACTIVITY = 1;
        public static final int TYPE_SHORTCUT = 2;
        public static final int TYPE_INFO     = 3;
        public static final int TYPE_DEEPLINK = 4;

        public String   label, subtitle, packageName, activityName, shortcutId;
        public Drawable icon;
        public int      type;
        public boolean  isLaunchable;

        ShortcutItem(String label, String subtitle, String pkg,
                     String act, String scId, Drawable icon, int type, boolean launchable) {
            this.label=label; this.subtitle=subtitle; this.packageName=pkg;
            this.activityName=act; this.shortcutId=scId;
            this.icon=icon; this.type=type; this.isLaunchable=launchable;
        }
    }

    // ══════════════════════════ ADAPTERS ════════════════════════════════════

    static class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {
        interface OnClick { void on(AppInfo a); }
        private final List<AppInfo> list;
        private final OnClick onClick;
        AppAdapter(List<AppInfo> l, OnClick c) { list=l; onClick=c; }

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
            h.tvArrow.setAlpha(1f);
            h.itemView.setAlpha(1f);
            h.itemView.setOnClickListener(v -> onClick.on(a));
        }
        @Override public int getItemCount() { return list.size(); }
        static class VH extends RecyclerView.ViewHolder {
            ImageView icon; TextView tvName, tvSub, tvArrow;
            VH(View v) { super(v);
                icon=v.findViewById(R.id.iv_app_icon);
                tvName=v.findViewById(R.id.tv_app_name);
                tvSub=v.findViewById(R.id.tv_app_pkg);
                tvArrow=v.findViewById(R.id.tv_app_arrow); }
        }
    }

    static class ShortcutAdapter extends RecyclerView.Adapter<ShortcutAdapter.VH> {
        interface OnClick { void on(ShortcutItem s); }
        private final List<ShortcutItem> list;
        private final OnClick onClick;
        ShortcutAdapter(List<ShortcutItem> l, OnClick c) { list=l; onClick=c; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_app, p, false));
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            ShortcutItem s = list.get(pos);
            h.tvName.setText(s.label);
            h.tvSub.setText(s.subtitle);
            h.icon.setImageDrawable(s.icon);
            // Badge by type
            switch (s.type) {
                case ShortcutItem.TYPE_MAIN:     h.tvArrow.setText("🏠"); break;
                case ShortcutItem.TYPE_SHORTCUT: h.tvArrow.setText("⚡"); break;
                case ShortcutItem.TYPE_DEEPLINK: h.tvArrow.setText("🔗"); break;
                case ShortcutItem.TYPE_INFO:     h.tvArrow.setText("ℹ️"); break;
                default:
                    h.tvArrow.setText(s.isLaunchable ? "✓" : "🔒");
                    break;
            }
            // Dim non-launchable items
            h.itemView.setAlpha(s.isLaunchable ? 1f : 0.6f);
            h.itemView.setOnClickListener(v -> onClick.on(s));
        }
        @Override public int getItemCount() { return list.size(); }
        static class VH extends RecyclerView.ViewHolder {
            ImageView icon; TextView tvName, tvSub, tvArrow;
            VH(View v) { super(v);
                icon=v.findViewById(R.id.iv_app_icon);
                tvName=v.findViewById(R.id.tv_app_name);
                tvSub=v.findViewById(R.id.tv_app_pkg);
                tvArrow=v.findViewById(R.id.tv_app_arrow); }
        }
    }
}
