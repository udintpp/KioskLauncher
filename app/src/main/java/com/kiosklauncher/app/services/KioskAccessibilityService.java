package com.kiosklauncher.app.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;

import com.kiosklauncher.app.activities.LauncherActivity;

/**
 * KioskAccessibilityService intercepts HOME and BACK button presses
 * system-wide to redirect back to the kiosk app.
 * Also detects if another app comes to foreground and brings back kiosk app.
 */
public class KioskAccessibilityService extends AccessibilityService {

    private SharedPreferences prefs;
    private boolean isHandling = false;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(LauncherActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isKioskEnabled()) return;

        int eventType = event.getEventType();
        CharSequence pkg = event.getPackageName();

        // Detect when another app comes to foreground
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != null) {
            String currentPkg = pkg.toString();
            String kioskPkg = prefs.getString(LauncherActivity.KEY_KIOSK_APP, null);

            // Allow system UI, our own app, and kiosk app
            if (kioskPkg != null &&
                !currentPkg.equals(kioskPkg) &&
                !currentPkg.equals(getPackageName()) &&
                !currentPkg.startsWith("com.android.systemui") &&
                !currentPkg.startsWith("com.android.launcher") &&
                !isHandling) {

                isHandling = true;
                redirectToKioskApp(kioskPkg);

                // Reset flag after delay
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> isHandling = false, 1500);
            }
        }
    }

    @Override
    public void onInterrupt() {
        // Service interrupted
    }

    private void redirectToKioskApp(String packageName) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
        }
    }

    private boolean isKioskEnabled() {
        return prefs != null && prefs.getBoolean(LauncherActivity.KEY_KIOSK_ENABLED, false);
    }
}
