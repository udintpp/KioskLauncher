package com.kiosklauncher.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import com.kiosklauncher.app.R;
import com.kiosklauncher.app.activities.LauncherActivity;

/**
 * KioskService runs as a foreground service to keep kiosk mode active.
 * Periodically checks if kiosk app is in foreground and relaunches if not.
 */
public class KioskService extends Service {

    private static final String CHANNEL_ID = "kiosk_channel";
    private static final int NOTIF_ID = 1001;
    private static final long CHECK_INTERVAL_MS = 2000; // 2 seconds

    private Handler handler;
    private Runnable kioskWatcher;
    private SharedPreferences prefs;
    private PowerManager.WakeLock wakeLock;
    private String kioskPackage;
    private boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(LauncherActivity.PREFS_NAME, Context.MODE_PRIVATE);
        createNotificationChannel();
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        kioskPackage = intent != null ?
                intent.getStringExtra("kiosk_package") :
                prefs.getString(LauncherActivity.KEY_KIOSK_APP, null);

        startForeground(NOTIF_ID, buildNotification());

        if (!running) {
            running = true;
            startKioskWatcher();
        }

        return START_STICKY; // Restart if killed
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        running = false;
        if (kioskWatcher != null) handler.removeCallbacks(kioskWatcher);
        releaseWakeLock();

        // Restart service if kiosk is still enabled
        if (prefs.getBoolean(LauncherActivity.KEY_KIOSK_ENABLED, false)) {
            Intent restartIntent = new Intent(this, KioskService.class);
            restartIntent.putExtra("kiosk_package", kioskPackage);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent);
            } else {
                startService(restartIntent);
            }
        }
        super.onDestroy();
    }

    // ─────────────────────── WATCHER ────────────────────────────────────────

    private void startKioskWatcher() {
        kioskWatcher = new Runnable() {
            @Override
            public void run() {
                if (!running) return;

                checkAndEnforceKioskMode();
                handler.postDelayed(this, CHECK_INTERVAL_MS);
            }
        };
        handler.postDelayed(kioskWatcher, CHECK_INTERVAL_MS);
    }

    private void checkAndEnforceKioskMode() {
        if (!prefs.getBoolean(LauncherActivity.KEY_KIOSK_ENABLED, false)) return;

        String pkg = prefs.getString(LauncherActivity.KEY_KIOSK_APP, null);
        if (pkg == null) return;

        // Update kiosk package if changed
        if (!pkg.equals(kioskPackage)) {
            kioskPackage = pkg;
            updateNotification();
        }
    }

    // ─────────────────────── NOTIFICATION ───────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Kiosk Mode",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Kiosk Launcher sedang aktif");
            channel.setShowBadge(false);

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent settingsIntent = new Intent(this, LauncherActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, settingsIntent,
                PendingIntent.FLAG_IMMUTABLE);

        String appName = kioskPackage != null ? kioskPackage : "Belum dipilih";
        try {
            if (kioskPackage != null) {
                android.content.pm.ApplicationInfo ai = getPackageManager()
                        .getApplicationInfo(kioskPackage, 0);
                appName = getPackageManager().getApplicationLabel(ai).toString();
            }
        } catch (Exception ignored) {}

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🔒 Kiosk Mode Aktif")
                .setContentText("App: " + appName)
                .setSmallIcon(R.drawable.ic_kiosk_notif)
                .setOngoing(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification());
    }

    // ─────────────────────── WAKELOCK ───────────────────────────────────────

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KioskLauncher:WakeLock");
            wakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }
}
