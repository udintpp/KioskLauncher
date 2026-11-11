package com.kiosklauncher.app.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kiosklauncher.app.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppPickerActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private AppAdapter adapter;
    private List<AppInfo> allApps = new ArrayList<>();
    private List<AppInfo> filteredApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);

        recyclerView = findViewById(R.id.recycler_apps);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        EditText searchBox = findViewById(R.id.et_search);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {
                filterApps(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadInstalledApps();

        adapter = new AppAdapter(filteredApps, app -> {
            selectApp(app);
        });
        recyclerView.setAdapter(adapter);
    }

    private void loadInstalledApps() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);

        for (ResolveInfo ri : resolveInfos) {
            String pkg = ri.activityInfo.packageName;
            // Skip our own app
            if (pkg.equals(getPackageName())) continue;

            String label = ri.loadLabel(pm).toString();
            Drawable icon = ri.loadIcon(pm);
            String activity = ri.activityInfo.name;

            allApps.add(new AppInfo(label, pkg, activity, icon));
        }

        Collections.sort(allApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        filteredApps.addAll(allApps);
    }

    private void filterApps(String query) {
        filteredApps.clear();
        for (AppInfo app : allApps) {
            if (app.label.toLowerCase().contains(query.toLowerCase()) ||
                app.packageName.toLowerCase().contains(query.toLowerCase())) {
                filteredApps.add(app);
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void selectApp(AppInfo app) {
        SharedPreferences prefs = getSharedPreferences(
                LauncherActivity.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(LauncherActivity.KEY_KIOSK_APP, app.packageName)
                .putString(LauncherActivity.KEY_KIOSK_ACTIVITY, app.activityName)
                .putBoolean(LauncherActivity.KEY_KIOSK_ENABLED, true)
                .apply();

        Toast.makeText(this,
                "✅ " + app.label + " dipilih sebagai Home App!",
                Toast.LENGTH_SHORT).show();
        finish();
    }

    // ─────────────────────── DATA CLASS ─────────────────────────────────────

    public static class AppInfo {
        public String label;
        public String packageName;
        public String activityName;
        public Drawable icon;

        public AppInfo(String label, String packageName, String activityName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.activityName = activityName;
            this.icon = icon;
        }
    }

    // ─────────────────────── ADAPTER ────────────────────────────────────────

    public static class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {
        private List<AppInfo> apps;
        private OnAppClickListener listener;

        public interface OnAppClickListener {
            void onAppClick(AppInfo app);
        }

        public AppAdapter(List<AppInfo> apps, OnAppClickListener listener) {
            this.apps = apps;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            AppInfo app = apps.get(position);
            holder.tvName.setText(app.label);
            holder.tvPkg.setText(app.packageName);
            holder.ivIcon.setImageDrawable(app.icon);
            holder.itemView.setOnClickListener(v -> listener.onAppClick(app));
        }

        @Override
        public int getItemCount() { return apps.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName, tvPkg;
            ViewHolder(View v) {
                super(v);
                ivIcon = v.findViewById(R.id.iv_app_icon);
                tvName = v.findViewById(R.id.tv_app_name);
                tvPkg = v.findViewById(R.id.tv_app_pkg);
            }
        }
    }
}
