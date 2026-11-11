package com.kiosklauncher.app.activities;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.kiosklauncher.app.R;
import com.kiosklauncher.app.admin.KioskDeviceAdmin;
import com.kiosklauncher.app.services.KioskService;

public class LauncherActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "KioskPrefs";
    public static final String KEY_KIOSK_APP = "kiosk_app_package";
    public static final String KEY_KIOSK_ACTIVITY = "kiosk_app_activity";
    public static final String KEY_KIOSK_ENABLED = "kiosk_enabled";
    public static final String KEY_LOCK_STATUS_BAR = "lock_status_bar";
    public static final String KEY_LOCK_NAV = "lock_navigation";
    public static final String KEY_ADMIN_PIN = "admin_pin";

    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean kioskEnabled = false;
    private String kioskPackage = null;
    private String kioskActivity = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        kioskEnabled = prefs.getBoolean(KEY_KIOSK_ENABLED, false);
        kioskPackage = prefs.getString(KEY_KIOSK_APP, null);
        kioskActivity = prefs.getString(KEY_KIOSK_ACTIVITY, null);

        // Start kiosk service
        startKioskService();

        if (kioskEnabled && kioskPackage != null) {
            // Show fullscreen + launch kiosk app
            setContentView(R.layout.activity_launcher_kiosk);
            applyKioskUI();
            handler.postDelayed(this::launchKioskApp, 300);
        } else {
            // First-time setup screen
            setContentView(R.layout.activity_launcher_setup);
            setupFirstRunUI();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-read prefs (might have changed in settings)
        kioskEnabled = prefs.getBoolean(KEY_KIOSK_ENABLED, false);
        kioskPackage = prefs.getString(KEY_KIOSK_APP, null);

        if (kioskEnabled && kioskPackage != null) {
            applyKioskUI();
            launchKioskApp();
        } else {
            setupFirstRunUI();
        }

        lockNavigationIfNeeded();
    }

    // ─────────────────────────── KIOSK LAUNCH ──────────────────────────────

    private void launchKioskApp() {
        if (kioskPackage == null) return;

        PackageManager pm = getPackageManager();
        Intent launchIntent;

        if (kioskActivity != null && !kioskActivity.isEmpty()) {
            launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.setComponent(new ComponentName(kioskPackage, kioskActivity));
        } else {
            launchIntent = pm.getLaunchIntentForPackage(kioskPackage);
        }

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                startActivity(launchIntent);
                // Start lock task (screen pinning)
                if (prefs.getBoolean(KEY_LOCK_NAV, true)) {
                    startLockTaskIfPossible();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Gagal membuka aplikasi: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Aplikasi tidak ditemukan: " + kioskPackage,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void startLockTaskIfPossible() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null && am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                startLockTask();
            }
        } catch (Exception e) {
            // Lock task not available without Device Owner
        }
    }

    // ─────────────────────────── UI SETUP ──────────────────────────────────

    private void applyKioskUI() {
        if (!prefs.getBoolean(KEY_LOCK_STATUS_BAR, true)) return;

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void setupFirstRunUI() {
        // Reset to normal view if not already setup view
        if (getWindow().getDecorView().getId() != R.layout.activity_launcher_setup) {
            setContentView(R.layout.activity_launcher_setup);
        }

        View btnSelectApp = findViewById(R.id.btn_select_app);
        View btnSettings = findViewById(R.id.btn_settings);
        View btnRequestAdmin = findViewById(R.id.btn_request_admin);

        if (btnSelectApp != null) {
            btnSelectApp.setOnClickListener(v -> openAppPicker());
        }
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> openSettings());
        }
        if (btnRequestAdmin != null) {
            btnRequestAdmin.setOnClickListener(v -> requestDeviceAdmin());
        }

        // Show currently selected app
        updateSelectedAppInfo();
    }

    private void updateSelectedAppInfo() {
        TextView tvCurrentApp = findViewById(R.id.tv_current_app);
        ImageView ivCurrentIcon = findViewById(R.id.iv_current_icon);
        if (tvCurrentApp == null) return;

        if (kioskPackage != null) {
            try {
                PackageManager pm = getPackageManager();
                android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(kioskPackage, 0);
                tvCurrentApp.setText("App: " + pm.getApplicationLabel(ai));
                if (ivCurrentIcon != null) {
                    ivCurrentIcon.setImageDrawable(pm.getApplicationIcon(kioskPackage));
                }
            } catch (PackageManager.NameNotFoundException e) {
                tvCurrentApp.setText("App: " + kioskPackage);
            }
        } else {
            tvCurrentApp.setText("Belum ada aplikasi dipilih");
        }
    }

    // ─────────────────────────── NAVIGATION LOCK ───────────────────────────

    private void lockNavigationIfNeeded() {
        if (!prefs.getBoolean(KEY_LOCK_NAV, true)) return;
        applyKioskUI();
    }

    @Override
    public void onBackPressed() {
        // Block back button - relaunch kiosk app
        if (kioskEnabled && kioskPackage != null) {
            launchKioskApp();
        }
        // Don't call super to prevent going back
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (kioskEnabled) {
            if (keyCode == KeyEvent.KEYCODE_HOME ||
                keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
                keyCode == KeyEvent.KEYCODE_BACK) {
                launchKioskApp();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ─────────────────────────── ACTIONS ───────────────────────────────────

    private void openAppPicker() {
        startActivity(new Intent(this, AppPickerActivity.class));
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void requestDeviceAdmin() {
        ComponentName adminComponent = new ComponentName(this, KioskDeviceAdmin.class);
        Intent intent = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Diperlukan untuk mode kiosk penuh");
        startActivity(intent);
    }

    private void startKioskService() {
        Intent serviceIntent = new Intent(this, KioskService.class);
        serviceIntent.putExtra("kiosk_package", kioskPackage);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    // Admin PIN unlock dialog
    public static void showAdminPinDialog(Context context, SharedPreferences prefs,
                                           Runnable onSuccess) {
        android.widget.EditText input = new android.widget.EditText(context);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Masukkan PIN Admin");

        new AlertDialog.Builder(context)
                .setTitle("🔐 Admin Access")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String pin = input.getText().toString();
                    String savedPin = prefs.getString(KEY_ADMIN_PIN, "1234");
                    if (pin.equals(savedPin)) {
                        if (onSuccess != null) onSuccess.run();
                    } else {
                        Toast.makeText(context, "PIN salah!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}
