# 🔒 Kiosk Launcher - Android Home Screen Takeover

**Aplikasi launcher Android yang mengambil alih home screen dan menjadikan aplikasi lain sebagai tampilan utama.**

---

## ✨ Fitur Utama

| Fitur | Keterangan |
|-------|-----------|
| 🏠 Home Screen Takeover | Mendaftarkan diri sebagai default launcher |
| 📱 App Picker | Pilih aplikasi apapun sebagai "home app" |
| 🔒 Navigation Lock | Blokir Back & Home button kembali ke app kiosk |
| 📶 Status Bar Lock | Sembunyikan status bar & navigation bar |
| 🛡️ Device Admin | Screen pinning / Lock Task Mode |
| ♿ Accessibility Guard | Monitor foreground app via AccessibilityService |
| 🔄 Auto-Start on Boot | Aktif otomatis saat perangkat dinyalakan |
| 🔐 Admin PIN | Proteksi pengaturan dengan PIN |
| 🔔 Foreground Service | Jalan terus sebagai foreground service |

---

## 🏗️ Arsitektur

```
app/
├── activities/
│   ├── LauncherActivity.java      # Home screen utama + kiosk launcher
│   ├── AppPickerActivity.java     # Pilih aplikasi kiosk
│   └── SettingsActivity.java      # Panel pengaturan admin
├── services/
│   ├── KioskService.java          # Foreground service (watchdog)
│   └── KioskAccessibilityService.java  # Monitor navigasi
├── receivers/
│   └── BootReceiver.java          # Auto-start saat boot
├── admin/
│   └── KioskDeviceAdmin.java      # Device Admin receiver
└── res/
    ├── layout/                    # Semua layout XML
    ├── xml/                       # Accessibility & Device Admin config
    └── values/                    # Strings, colors, themes
```

---

## 🚀 Cara Setup (Setelah Install APK)

1. **Set Default Launcher** → Tekan Home button → Pilih "Kiosk Launcher" → "Always"
2. **Pilih Aplikasi Kiosk** → Tap "Pilih Aplikasi Kiosk" → Cari & pilih app
3. **Aktifkan Device Admin** → Tap "Aktifkan Device Admin" → Setujui
4. **Aktifkan Accessibility Service** → Settings → Accessibility → Kiosk Navigation Guard → ON
5. **Aktifkan Kiosk Mode** → Pengaturan → Toggle "Kiosk Mode" ON
6. **Reboot** → Perangkat akan langsung boot ke app kiosk

---

## 🔐 Keluar dari Kiosk Mode

1. Tekan tombol Power → Ketuk notifikasi "Kiosk Mode Aktif"
2. Masukkan PIN Admin (default: **1234**)
3. Toggle Kiosk Mode → OFF

---

## 📋 Permissions yang Dibutuhkan

- `RECEIVE_BOOT_COMPLETED` - Auto-start saat boot
- `FOREGROUND_SERVICE` - Background service
- `SYSTEM_ALERT_WINDOW` - Overlay untuk lock
- `QUERY_ALL_PACKAGES` - List semua aplikasi
- `BIND_ACCESSIBILITY_SERVICE` - Guard navigasi
- `BIND_DEVICE_ADMIN` - Lock task mode
- `WAKE_LOCK` - Jaga layar tetap aktif

---

## 🔧 Build

```bash
./gradlew assembleDebug     # Debug APK
./gradlew assembleRelease   # Release APK
```

---

## ⚠️ Catatan Penting

- **Lock Task Mode penuh** membutuhkan Device Owner. Tanpa itu, screen pinning butuh konfirmasi user.
- **AccessibilityService** adalah cara paling efektif untuk intercept navigasi tanpa root.
- Untuk **unlock penuh** (root-level), perlu `adb shell dpm set-device-owner` atau gunakan MDM.

---

## 📝 License

MIT License - Open Source
