# Saldo Tracking

Personal Android APK (`dev.zurarul.saldotracking`) that automatically tracks your combined Rupiah balance (**saldo**) from **DANA**, **GoPay**, **Livin' by Mandiri**, **ShopeePay**, **OVO**, **BCA**, and **SeaBank** push notifications, with green (`+`) vs. red (`-`) cashflow stats and charts.

## Key Features
- **Automatic Notification Tracking (`NotificationListenerService`):** Starts from your user-set Base Balance; outgoing payments subtract (`-` red) and incoming money adds (`+` green) automatically.
- **Natural Self-Transfer Handling:** Moving money between your own apps (e.g., Livin' Mandiri -> DANA) records both sides naturally (`-Rp 101.000` and `+Rp 100.000`), so your total saldo drops only by the admin fee (or stays flat if free).
- **Anti-Spam, Liquid-Saldo & Duplicate Guards:**
  - Ignores promo/cashback/voucher marketing spam and OTP alerts (`IGNORED_PROMO`).
  - Ignores failed, pending, cancelled, or expired transactions (`IGNORED_FAILED`).
  - Ignores PayLater, Credit Cards, Coins/Points, internal savings pockets, and generic Gojek/Shopee marketplace orders that don't explicitly move GoPay/ShopeePay saldo (`IGNORED_NON_SALDO`).
  - Deduplicates repeated or paired-package (`Gojek` + `GoPay`, `Shopee` + `ShopeePay`) notifications within 15 seconds (`IGNORED_DUPLICATE`).
- **Cashflow Stats & Charts (`Stats` tab):**
  - Switch between **Harian** (daily in month), **Bulanan** (Jan–Dec in year), and **Tahunan** (all years).
  - **Rasio Pemasukan vs Pengeluaran** donut chart, split ratio bar, and net cashflow badge.
  - Interactive paired green (`+`) / red (`-`) bar chart with tap-to-inspect details.
  - **Ringkasan per Aplikasi** breakdown.
- **Built for OPPO A17 (Android 12 / ColorOS 12.1):**
  - Lightweight pure Jetpack Compose Canvas charts & local Room SQLite (zero `INTERNET` permission; 100% offline on-device privacy).
  - Quiet, low-priority **Keep-Alive Notification** foreground service (toggleable in Settings, ON by default) + ColorOS setup checklist.
  - **Notification Inspector** storing the last 100 notifications locally with 1-tap `+ Catat sebagai Transaksi` promotion for any unparsed notification.
  - **Backup Lokal:** Export and Import your entire transaction history and base balance as JSON.

---

## Download the APK from GitHub Actions

Every push to `main` runs unit tests and builds a signed release APK on GitHub Actions:
1. Open the [**Actions** tab of `zuraruldev/saldo-tracking-apps`](https://github.com/zuraruldev/saldo-tracking-apps/actions).
2. Click the latest green **Build APK** workflow run.
3. Scroll down to **Artifacts** and download **`SaldoTracking-v1.0.<run_number>`**.
4. Extract the `.zip` file to get **`app-release.apk`**.

> **In-place upgrades:** The repository uses a consistent shared signing keystore (`app/saldo-shared.keystore`) so every newly downloaded APK from GitHub Actions installs cleanly right over your existing app without uninstalling or wiping your saldo data.

---

## Install & OPPO A17 (ColorOS 12.1) Setup

1. **Install APK:** Open `app-release.apk` on your OPPO A17 (allow *"Install unknown apps"* for your browser or file manager if prompted).
2. **Onboarding:** Enter your current combined starting balance (**Base Balance**) and tap **Izinkan Akses Notifikasi** -> enable **Saldo Tracking**, then tap **Lanjut**.
3. **ColorOS Background Keep-Alive (Settings tab inside app):**
   - Tap **Izin Baterai** to exempt Saldo Tracking from battery optimization.
   - On OPPO / ColorOS 12.1: open **Settings › Battery › App battery management › Saldo Tracking** -> turn ON **Allow background activity** and **Allow auto launch**.
   - Open your phone's **Recents** switcher -> tap `⋮` on **Saldo Tracking** -> tap **Lock**.
   - Keep **Keep-Alive Notification** enabled in the app's Settings tab.
4. **Verify & Inspect:**
   - Check that the Home screen top-right badge shows **`Auto-Track ON`** (green).
   - After your next DANA, GoPay, or Livin' Mandiri transaction, open the app to see your updated saldo.
   - If a notification ever uses new wording and isn't captured automatically, open **Settings › Notification Inspector** to view the raw notification text and tap **`+ Catat sebagai Transaksi`** to record it in one tap.

---

## Local Build & Test

```bash
./gradlew testDebugUnitTest assembleRelease
```

The signed APK is output to `app/build/outputs/apk/release/app-release.apk`.
