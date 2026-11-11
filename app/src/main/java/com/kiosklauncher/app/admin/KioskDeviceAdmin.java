package com.kiosklauncher.app.admin;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Device Admin Receiver for kiosk lock task mode.
 * Required for startLockTask() without user prompt on some devices.
 */
public class KioskDeviceAdmin extends DeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, "✅ Device Admin diaktifkan - Kiosk mode tersedia",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Toast.makeText(context, "⚠️ Device Admin dinonaktifkan - Kiosk mode terbatas",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Menonaktifkan Device Admin akan membatasi kemampuan kiosk mode. Lanjutkan?";
    }
}
