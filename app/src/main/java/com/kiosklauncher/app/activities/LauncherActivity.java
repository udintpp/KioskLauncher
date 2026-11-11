package com.kiosklauncher.app.activities;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
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

    // ─── SharedPreferences Keys ───────────────────────────────────────────
    public static final String PREFS_NAME           = "KioskPrefs";
    public static final String KEY_KIOSK_APP        = "kiosk_app_package";
    public static final String KEY_KIOSK_ACTIVITY   = "kiosk_app_activity";
    public static final String KEY_KIOSK_SHORTCUT_ID= "kiosk_shortcut_id";
    public static final String KEY_KIOSK_TYPE       = "kiosk_type";      // 0=main,1=activity,2=shortcut
    public static final String KEY_KIOSK_ENABLED    = "kiosk_enabled";
    public static final String KEY_LOCK_STATUS_BAR  = "lock_status_bar";
    public static final String KEY_LOCK_NAV         = "lock_navigation";
    public static final String KEY_ADMIN_PIN        = "admin_pin";

    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean kioskEnabled = false;
    private String  kioskPackage = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        prefs         = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        kioskEnabled  = prefs.getBoolean(KEY_KIOSK_ENABLED, false);
        kioskPackage  = prefs.getString(KEY_KIOSK_APP, null);

        startKioskService();

        if (kioskEnabled && kioskPackage != null) {
            setContentView(R.layout.activity_launcher_kiosk);
            applyKioskUI();
            handler.postDelayed(this::launchKioskTarget, 400);
        } else {
            setContentView(R.layout.activity_launcher_setup);
            setupFirstRunUI();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        kioskEnabled = prefs.getBoolean(KEY_KIOSK_ENABLED, false);
        kioskPackage = prefs.getString(KEY_KIOSK_APP, null);

        if (kioskEnabled && kioskPackage != null) {
            applyKioskUI();
            launchKioskTarget();
        } else {
            if (!(getWindow().getDecorView().getTag() instanceof String &&
                  getWindow().getDecorView().getTag().equals("setup"))) {
                setContentView(R.layout.activity_launcher_setup);
                getWindow().getDecorView().setTag("setup");
            }
            setupFirstRunUI();
        }
        lockNavigationIfNeeded();
    }

    // ─────────────────────────── LAUNCH KIOSK TARGET ────────────────────────

    private void launchKioskTarget() {
        if (kioskPackage == null) return;

        int    type       = prefs.getInt   (KEY_KIOSK_TYPE,         0);
        String activity   = prefs.getString(KEY_KIOSK_ACTIVITY,     "");
        String shortcutId = prefs.getString(KEY_KIOSK_SHORTCUT_ID,  "");

        switch (type) {
            case AppPickerActivity.ShortcutItem.TYPE_SHORTCUT:
                launchShortcut(shortcutId);
                break;
            case AppPickerActivity.ShortcutItem.TYPE_ACTIVITY:
                launchActivity(activity);
                break;
            default: // TYPE_MAIN
                launchMainApp();
                break;
        }
    }

    /** Launch main launcher intent of kiosk app */
    private void launchMainApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(kioskPackage);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startSafely(intent);
        } else {
            toastError("App tidak ditemukan: " + kioskPackage);
        }
    }

    /** Launch a specific Activity by component name */
    private void launchActivity(String activityName) {
        if (activityName == null || activityName.isEmpty()) {
            launchMainApp();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName(kioskPackage, activityName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startSafely(intent);
    }

    /** Launch a pinned/dynamic/static App Shortcut */
    private void launchShortcut(String shortcutId) {
        if (shortcutId == null || shortcutId.isEmpty()) {
            launchMainApp();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                LauncherApps launcherApps =
                        (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
                if (launcherApps != null) {
                    UserHandle user = android.os.Process.myUserHandle();
                    launcherApps.startShortcut(kioskPackage, shortcutId, null, null, user);
                    if (prefs.getBoolean(KEY_LOCK_NAV, true)) {
                        startLockTaskIfPossible();
                    }
                    return;
                }
            } catch (Exception e) {
                // Fallback to main app if shortcut launch fails
                toastError("Shortcut gagal, buka halaman utama");
            }
        }
        launchMainApp();
    }

    private void startSafely(Intent intent) {
        try {
            startActivity(intent);
            if (prefs.getBoolean(KEY_LOCK_NAV, true)) {
                startLockTaskIfPossible();
            }
        } catch (Exception e) {
            toastError("Gagal membuka: " + e.getMessage());
        }
    }

    private void startLockTaskIfPossible() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null &&
                am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                startLockTask();
            }
        } catch (Exception ignored) {}
    }

    // ─────────────────────────── UI SETUP ───────────────────────────────────

    private void applyKioskUI() {
        if (!prefs.getBoolean(KEY_LOCK_STATUS_BAR, true)) return;
        WindowInsetsControllerCompat ctrl = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        ctrl.hide(WindowInsetsCompat.Type.systemBars());
        ctrl.setSystemBarsBehavior(
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
        View btnSelectApp    = findViewById(R.id.btn_select_app);
        View btnSettings     = findViewById(R.id.btn_settings);
        View btnRequestAdmin = findViewById(R.id.btn_request_admin);

        if (btnSelectApp    != null) btnSelectApp.setOnClickListener(v -> openAppPicker());
        if (btnSettings     != null) btnSettings.setOnClickListener(v -> openSettings());
        if (btnRequestAdmin != null) btnRequestAdmin.setOnClickListener(v -> requestDeviceAdmin());

        updateSelectedAppInfo();
    }

    private void updateSelectedAppInfo() {
        TextView tvCurrentApp  = findViewById(R.id.tv_current_app);
        ImageView ivCurrentIcon = findViewById(R.id.iv_current_icon);
        if (tvCurrentApp == null) return;

        if (kioskPackage != null) {
            try {
                PackageManager pm = getPackageManager();
                android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(kioskPackage, 0);
                String appName = pm.getApplicationLabel(ai).toString();

                int    type       = prefs.getInt(KEY_KIOSK_TYPE, 0);
                String activity   = prefs.getString(KEY_KIOSK_ACTIVITY, "");
                String shortcutId = prefs.getString(KEY_KIOSK_SHORTCUT_ID, "");

                String detail;
                switch (type) {
                    case AppPickerActivity.ShortcutItem.TYPE_SHORTCUT:
                        detail = appName + "\n⚡ Shortcut: " + shortcutId;
                        break;
                    case AppPickerActivity.ShortcutItem.TYPE_ACTIVITY:
                        String actShort = activity.contains(".") ?
                                activity.substring(activity.lastIndexOf('.') + 1) : activity;
                        detail = appName + "\n📄 " + actShort;
                        break;
                    default:
                        detail = appName + "\n🏠 Halaman Utama";
                        break;
                }
                tvCurrentApp.setText(detail);
                if (ivCurrentIcon != null)
                    ivCurrentIcon.setImageDrawable(pm.getApplicationIcon(kioskPackage));
            } catch (PackageManager.NameNotFoundException e) {
                tvCurrentApp.setText(kioskPackage);
            }
        } else {
            tvCurrentApp.setText("Belum ada aplikasi dipilih");
        }
    }

    // ─────────────────────────── NAVIGATION LOCK ────────────────────────────

    private void lockNavigationIfNeeded() {
        if (prefs.getBoolean(KEY_LOCK_STATUS_BAR, true)) applyKioskUI();
    }

    @Override
    public void onBackPressed() {
        if (kioskEnabled && kioskPackage != null) {
            launchKioskTarget();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (kioskEnabled) {
            if (keyCode == KeyEvent.KEYCODE_HOME ||
                keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
                keyCode == KeyEvent.KEYCODE_BACK) {
                launchKioskTarget();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ─────────────────────────── ACTIONS ────────────────────────────────────

    private void openAppPicker()   { startActivity(new Intent(this, AppPickerActivity.class)); }
    private void openSettings()    { startActivity(new Intent(this, SettingsActivity.class)); }

    private void requestDeviceAdmin() {
        ComponentName admin = new ComponentName(this, KioskDeviceAdmin.class);
        Intent intent = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
        intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Diperlukan untuk mode kiosk penuh");
        startActivity(intent);
    }

    private void startKioskService() {
        Intent svc = new Intent(this, KioskService.class);
        svc.putExtra("kiosk_package", kioskPackage);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);
    }

    private void toastError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ─────────────────────────── ADMIN PIN DIALOG ───────────────────────────

    public static void showAdminPinDialog(Context ctx, SharedPreferences prefs, Runnable onSuccess) {
        android.widget.EditText input = new android.widget.EditText(ctx);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                           android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("PIN Admin");

        new AlertDialog.Builder(ctx)
                .setTitle("🔐 Admin Access")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String pin   = input.getText().toString();
                    String saved = prefs.getString(KEY_ADMIN_PIN, "1234");
                    if (pin.equals(saved)) { if (onSuccess != null) onSuccess.run(); }
                    else Toast.makeText(ctx, "PIN salah!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}
