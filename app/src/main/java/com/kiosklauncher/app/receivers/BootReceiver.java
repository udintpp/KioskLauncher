package com.kiosklauncher.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.kiosklauncher.app.activities.LauncherActivity;
import com.kiosklauncher.app.services.KioskService;

/**
 * BootReceiver - starts KioskService automatically on device boot.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
            "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {

            SharedPreferences prefs = context.getSharedPreferences(
                    LauncherActivity.PREFS_NAME, Context.MODE_PRIVATE);

            if (prefs.getBoolean(LauncherActivity.KEY_KIOSK_ENABLED, false)) {
                String kioskPkg = prefs.getString(LauncherActivity.KEY_KIOSK_APP, null);

                // Start kiosk service
                Intent serviceIntent = new Intent(context, KioskService.class);
                serviceIntent.putExtra("kiosk_package", kioskPkg);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }

                // Launch kiosk app
                if (kioskPkg != null) {
                    Intent appIntent = context.getPackageManager()
                            .getLaunchIntentForPackage(kioskPkg);
                    if (appIntent != null) {
                        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                        context.startActivity(appIntent);
                    }
                }
            }
        }
    }
}
