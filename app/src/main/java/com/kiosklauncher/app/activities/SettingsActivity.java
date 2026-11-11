package com.kiosklauncher.app.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.kiosklauncher.app.R;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(LauncherActivity.PREFS_NAME, Context.MODE_PRIVATE);

        // ─── Kiosk Toggle ───────────────────────────────────────────────────
        Switch switchKiosk = findViewById(R.id.switch_kiosk);
        switchKiosk.setChecked(prefs.getBoolean(LauncherActivity.KEY_KIOSK_ENABLED, false));
        switchKiosk.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(LauncherActivity.KEY_KIOSK_ENABLED, checked).apply();
            Toast.makeText(this, checked ? "Kiosk mode ON" : "Kiosk mode OFF",
                    Toast.LENGTH_SHORT).show();
        });

        // ─── Lock Status Bar ────────────────────────────────────────────────
        Switch switchStatusBar = findViewById(R.id.switch_lock_status_bar);
        switchStatusBar.setChecked(prefs.getBoolean(LauncherActivity.KEY_LOCK_STATUS_BAR, true));
        switchStatusBar.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(LauncherActivity.KEY_LOCK_STATUS_BAR, checked).apply());

        // ─── Lock Navigation ────────────────────────────────────────────────
        Switch switchNavigation = findViewById(R.id.switch_lock_navigation);
        switchNavigation.setChecked(prefs.getBoolean(LauncherActivity.KEY_LOCK_NAV, true));
        switchNavigation.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(LauncherActivity.KEY_LOCK_NAV, checked).apply());

        // ─── Admin PIN ──────────────────────────────────────────────────────
        EditText etPin = findViewById(R.id.et_admin_pin);
        etPin.setText(prefs.getString(LauncherActivity.KEY_ADMIN_PIN, "1234"));

        Button btnSavePin = findViewById(R.id.btn_save_pin);
        btnSavePin.setOnClickListener(v -> {
            String pin = etPin.getText().toString().trim();
            if (pin.length() < 4) {
                Toast.makeText(this, "PIN minimal 4 digit!", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString(LauncherActivity.KEY_ADMIN_PIN, pin).apply();
            Toast.makeText(this, "✅ PIN disimpan!", Toast.LENGTH_SHORT).show();
        });

        // ─── Current App Info ───────────────────────────────────────────────
        updateSelectedInfo();

        // ─── Select App / Shortcut Button ───────────────────────────────────
        Button btnSelectApp = findViewById(R.id.btn_pick_app);
        btnSelectApp.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, AppPickerActivity.class)));

        // ─── Reset Button ───────────────────────────────────────────────────
        Button btnReset = findViewById(R.id.btn_reset);
        btnReset.setOnClickListener(v ->
                LauncherActivity.showAdminPinDialog(this, prefs, () -> {
                    prefs.edit().clear().apply();
                    Toast.makeText(this, "✅ Reset berhasil. Restart app.", Toast.LENGTH_SHORT).show();
                    finish();
                }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSelectedInfo();
    }

    private void updateSelectedInfo() {
        TextView tvSelectedApp = findViewById(R.id.tv_selected_app);
        if (tvSelectedApp == null) return;

        String pkg        = prefs.getString(LauncherActivity.KEY_KIOSK_APP, null);
        int    type       = prefs.getInt   (LauncherActivity.KEY_KIOSK_TYPE, 0);
        String activity   = prefs.getString(LauncherActivity.KEY_KIOSK_ACTIVITY, "");
        String shortcutId = prefs.getString(LauncherActivity.KEY_KIOSK_SHORTCUT_ID, "");

        if (pkg == null) {
            tvSelectedApp.setText("Belum ada app dipilih");
            return;
        }

        String appName = pkg;
        try {
            android.content.pm.ApplicationInfo ai =
                    getPackageManager().getApplicationInfo(pkg, 0);
            appName = getPackageManager().getApplicationLabel(ai).toString();
        } catch (Exception ignored) {}

        String detail;
        switch (type) {
            case AppPickerActivity.ShortcutItem.TYPE_SHORTCUT:
                detail = "📦 " + appName + "\n⚡ Shortcut ID: " + shortcutId;
                break;
            case AppPickerActivity.ShortcutItem.TYPE_ACTIVITY:
                String actShort = activity.contains(".") ?
                        activity.substring(activity.lastIndexOf('.') + 1) : activity;
                detail = "📦 " + appName + "\n📄 Activity: " + actShort;
                break;
            default:
                detail = "📦 " + appName + "\n🏠 Halaman Utama";
                break;
        }
        tvSelectedApp.setText(detail);
    }
}
