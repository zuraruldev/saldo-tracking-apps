# Saldo Tracking — Written Design Document

**Date:** 2026-11-23  
**Project:** Saldo Tracking (Personal Android APK)  
**Repository:** `https://github.com/zuraruldev/saldo-tracking-apps.git`  
**Obsidian Second Brain:** `/home/zura/Obsidian Vault/antigravity/antigravity project/apps money tracking`  
**App Icon Asset:** `anime.jpg` (in the Obsidian folder above)

---

## 1. Intent & The Why

Whenever the user makes a payment or receives money across Indonesian e-wallets and banking apps (primarily **DANA**, **GoPay**, and **Livin' by Mandiri**), they want their total money balance (**saldo**) to stay up to date automatically without manually typing every transaction.

Because Indonesian consumer e-wallets and banks do not provide open personal APIs, the app works **100% locally on the user's Android phone** by reading incoming transaction push notifications in real time, updating a user-set base balance, and presenting clear green (`+`) vs. red (`-`) cashflow stats and charts.

The user is new to mobile app development and wants a clean, reliable, beginner-friendly project developed in their GitHub repository where **GitHub Actions automatically builds a downloadable APK** they can install directly on their **OPPO A17**.

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals (v1)
- **Automatic balance tracking from notifications:** Start from a user-defined base balance; outgoing transaction notifications subtract (`-`) from it and incoming transaction notifications add (`+`) to it automatically.
- **Natural self-transfer handling:** Moving money between the user's own apps (e.g., Livin' Mandiri -> DANA) records both sides naturally (`-Rp 101.000` and `+Rp 100.000`), so total saldo automatically drops only by any admin fee (or stays flat if free).
- **Clear visual cashflow stats:** Green (`+`) for pemasukan (inflow) and red (`-`) for pengeluaran (outflow), an inflow-vs-outflow ratio view, and paired green/red trend charts across **Harian** (daily), **Bulanan** (monthly), and **Tahunan** (yearly) periods.
- **Reliable on OPPO A17 (Android 12 / ColorOS 12.1):** Lightweight performance for a 4GB RAM device, live listener status indicator, self-healing rebind, and guided ColorOS battery/auto-launch setup.
- **Frictionless APK delivery:** GitHub Actions runs unit tests and builds an installable APK downloadable directly from the Actions tab, signed with a consistent debug keystore so updates install cleanly over older versions without wiping local data.
- **Custom identity:** Uses `anime.jpg` as the Android APK launcher icon and follows the phone's Dark/Light system theme automatically.
- **Learning-friendly documentation:** Design, phased roadmap, and implementation plans live in the user's Obsidian folder (and sync into `docs/superpowers/` inside the repo).

### Non-Goals (Explicitly Deferred Out of v1)
- **No VPS / backend / cloud sync in v1** (saved for Phase 3 as an optional upgrade for cloud backup, web dashboard, or Telegram summaries).
- **No Google Play Store publishing** (personal sideloaded APK only).
- **No separate per-wallet balance ledgers** (single combined base balance + per-app transaction stats only).
- **No automated bank logins, scraping, SMS scraping, or email polling in v1.**
- **No multi-currency support** (Indonesian Rupiah `Rp` only).
- **No expense categorization AI or budgeting alerts in v1.**

### Anti-Goals (What Would Make This a Failure Even If It Runs)
- **Promo/cashback spam corrupting the balance:** Marketing notifications like *"Cashback hingga Rp 50.000!"* or *"Voucher diskon Rp 25.000"* MUST NEVER be counted as real money.
- **Silent background death on ColorOS:** If OPPO kills the notification listener, the user must see immediately on Home that auto-tracking is paused and be guided straight to fix it.
- **Data loss on APK upgrades:** Installing a newly built APK from GitHub Actions must NOT fail with signature conflicts or wipe existing transactions.
- **Silent missed transactions with no visibility:** If an e-wallet changes its notification wording, the user must be able to inspect the raw notification log locally and promote a missed item into a real transaction in one tap.

---

## 3. Constraints & Environment

- **Target Device:** OPPO A17 running Android 12 (API 31) / ColorOS 12.1, MediaTek Helio G35, 4GB RAM.
  - `minSdk` MUST be `<= 31` (recommended `minSdk = 26`, `targetSdk = 34`).
  - Keep memory and CPU footprint minimal (avoid heavy WebView chart wrappers or bloated runtime dependencies).
  - **Keep-Alive Foreground Service (Recommended — User Accepted Recommendation):** Run a quiet, low-priority (silent, no sound/vibration) Foreground Service notification (`"Auto-Track Active"`) **ON by default** with an opt-out toggle in Settings (`Keep-Alive Notification`). Declares `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` (for `targetSdk = 34`), and `POST_NOTIFICATIONS` (runtime-requested on API 33+, auto-granted on Android 12). Starts when the user opens the app or toggles it on, and handles Android 12+ `ForegroundServiceStartNotAllowedException` gracefully on background/boot restarts by falling back to pure `NotificationListenerService` + `requestRebind()` without crashing.
- **Local-First Privacy:** All transactions, balances, and raw notification debug logs stay strictly on-device in local SQLite (Room). No network permission is required in v1.
- **Tech Stack (Decided):** Native Android using **Kotlin**, **Jetpack Compose (Material 3)**, **Room (SQLite)**, **Coroutines/Flow**, and Android **`NotificationListenerService`**.
- **CI/CD Delivery (Decided):** GitHub repository `https://github.com/zuraruldev/saldo-tracking-apps.git` with a GitHub Actions workflow that runs unit tests and uploads an installable APK artifact on every push to `main` and manual dispatch.
- **Consistent Signing for Updates (Builder Call — Verified Needed for GitHub Actions):** Because ephemeral GitHub Actions runners generate a different random `~/.android/debug.keystore` on every run, the repository commits a dedicated non-secret project keystore (`app/saldo-shared.keystore`) used by both the `debug` and `release` signing configs so every downloaded APK updates in-place cleanly on the OPPO A17 without uninstalling.
- **CI Ships the Release Variant (Builder Call, added during planning):** GitHub Actions builds the non-debuggable `release` variant (no code shrinking) because debuggable Compose builds run noticeably slower on the Helio G35. `versionCode` = GitHub run number so each download is a newer version.
- **App Icon Asset:** `/home/zura/Obsidian Vault/antigravity/antigravity project/apps money tracking/anime.jpg` must be bundled into the app's Android adaptive/legacy launcher icon resources (`mipmap-*`).

---

## 4. Product & Functional Design

### 4.1 Balance Calculation & Editing Model
- **Formula:**  
  `currentSaldo = baseBalance + SUM(transactions where type == INCOME) - SUM(transactions where type == EXPENSE)`
- **First Launch / Onboarding:**
  - Prompt the user to enter their initial **Base Balance** (can be `Rp 0` or their current total across DANA + GoPay + Livin') and walk them through granting **Notification Access** + OPPO background settings.
- **Edit Base (Reconciliation):**
  - Tapping **Edit Base** on Home opens a simple dialog showing the current calculated saldo and lets the user enter their **actual current saldo right now**.
  - Saving recalculates `baseBalance = enteredCurrentSaldo - (totalIncome - totalExpense)` behind the scenes. This corrects any balance drift cleanly **without** inserting fake income/expense transactions that would distort the Pemasukan vs. Pengeluaran charts.
  - (Also shows the underlying base offset for transparency.)
- **Transactions (Auto & Manual):**
  - Every transaction stores: `id`, `amountIdr` (`Long`, positive Rupiah integer), `type` (`INCOME` or `EXPENSE`), `sourceApp` (e.g., `DANA`, `GoPay`, `Livin' Mandiri`, `ShopeePay`, `Manual`, etc.), `sourcePackage` (nullable Android package name), `title` (short description), `rawNotificationText` (nullable), `timestampMillis`, `isManual` (`Boolean`).
  - Users can **Add Manual (`+ Manual`)**, **Edit** any existing transaction (amount, `+`/`-` direction, source, title, date/time), or **Delete** any transaction. All edits reactively recalculate Home saldo and Stats charts immediately.

### 4.2 Tracked Apps & Notification Parsing Pipeline
- **Primary Apps (tuned and tested first, enabled by default):**
  1. **DANA** — `id.dana`
  2. **GoPay / Gojek** — `com.gojek.gopay`, `com.gojek.app`
  3. **Livin' by Mandiri** — `id.bmri.livin`
- **Secondary Apps ("just in case", enabled by default and individually toggleable in Settings):**
  4. **Shopee / ShopeePay** — `com.shopee.id`, `com.shopeepay.id`
  5. **OVO** — `ovo.id`
  6. **BCA mobile / myBCA** — `com.bca`, `com.bca.mybca.omni.android`
  7. **SeaBank** — `id.co.bankbkemobile.digitalbank`
- **Super-App Strict Wallet-Only Matching (My Call):**
  - Inside super-apps (`com.gojek.app` and `com.shopee.id`), notifications MUST explicitly indicate a **GoPay** or **ShopeePay** wallet balance movement (e.g., mentioning `GoPay` / `ShopeePay` saldo deduction/top-up). Generic ride, food, or marketplace order confirmations (which might be paid via COD/cash or via Livin' Mandiri VA / DANA) MUST be ignored so they never double-deduct alongside the real bank/wallet notification. Logged as `IGNORED_NON_SALDO` if rejected.
- **Pipeline Stages (for every notification posted to `NotificationListenerService`):**
  1. **Package & Flag Filter:** Ignore notifications from untracked/disabled packages, `FLAG_ONGOING_EVENT`, `FLAG_GROUP_SUMMARY`, or empty title+text.
  2. **Promo / Spam Guard:** Reject marketing, OTP, and non-transaction notifications (e.g., containing promo/voucher/cashback-offer/diskon/klaim/hingga/OTP/kode verifikasi signals without completed-transaction confirmation). Log decision as `IGNORED_PROMO`.
  2b. **Failed / Pending Guard (Builder Call, added during planning):** Reject notifications for transactions that did not complete (`gagal`, `dibatalkan`, `pending`, `menunggu`, `sedang diproses`, `kedaluwarsa`, `ditolak`, …) so a failed payment never moves the saldo. Log decision as `IGNORED_FAILED` (Inspector badge `Gagal/Pending`).
  3. **Liquid-Saldo-Only Filter (Decided — User Does Not Use PayLater):** Strictly track liquid bank/e-wallet balances. Reject notifications referencing non-liquid instruments or reward currencies: PayLater/installments (`GoPayLater`, `SPayLater`, `DANA Cicil`, `Livin' Paylater`, `PayLater`, `Cicilan`), Credit Cards (`Kartu Kredit`, `Credit Card`), loyalty points/coins (`GoPay Coins`, `Koin Shopee`, `DANA Points`), and internal savings/investment transfers (`DANA Goals`, `GoPay Tabungan`, `Deposito`, `Reksa Dana`). Log decision as `IGNORED_NON_SALDO`.
  4. **Amount Extraction:** Parse Indonesian Rupiah amounts (`Rp 50.000`, `Rp50.000`, `Rp 1.250.000,00`, `IDR 50,000.00`, `IDR 50.000`). If no valid positive amount is found, log as `IGNORED_UNPARSED`.
  5. **Direction Classification (`INCOME` vs `EXPENSE`):** Apply per-app rules first (to handle app-specific phrasing like DANA's *"Isi saldo berhasil"* = `INCOME` vs. Livin's *"Top up DANA berhasil"* = `EXPENSE`, and strict wallet wording on `com.gojek.app` / `com.shopee.id`), then fallback Indonesian/English keyword rules. If direction cannot be determined safely, log as `IGNORED_UNPARSED` rather than guessing wrong.
  6. **Cross-Package Deduplication Guard (My Call):** Compute a wallet-group fingerprint `(sourceApp, amountIdr, type)` (normalizing paired packages—e.g. `com.gojek.app` & `com.gojek.gopay` -> `GoPay`; `com.shopee.id` & `com.shopeepay.id` -> `ShopeePay`). If an identical wallet-group fingerprint was captured within the last **15 seconds**, skip it and log as `IGNORED_DUPLICATE`. This prevents both same-package reposts and paired super-app + standalone wallet double-counts.
  7. **Persist & Notify UI:** Insert transaction into Room (which updates `StateFlow` for Home & Stats automatically) and record an audit entry (`CAPTURED`) in the local raw notification log (capped to the most recent 100 entries so storage stays tiny).

### 4.3 Screens & Visual Design
- **Theme (Decided):** **Follow Phone Theme** automatically (Dark Fintech palette when Android is in Dark Mode; Clean Light palette when Android is in Light Mode). Optional theme override in Settings (`Ikuti Sistem` [default] / `Gelap` / `Terang`).
- **Color Language (Decided):**
  - **Green** (`+`) strictly for **Pemasukan** (inflow / positive net).
  - **Red** (`-`) strictly for **Pengeluaran** (outflow / negative net).
- **Formatting & Copy:** Indonesian Rupiah (`Rp 2.450.000`) with Indonesian/clean mixed UI labels (`Total Saldo`, `Pemasukan`, `Pengeluaran`, `Harian`, `Bulanan`, `Tahunan`, `Transaksi Terbaru`, `Auto-Track ON`).
- **3-Tab Bottom Navigation:**
  1. **Home:**
     - Top bar: App icon + **Saldo Tracking** title + live **Auto-Track ON / PAUSED** status pill (tapping PAUSED opens Setup).
     - Hero **Total Saldo** card: Large current Rupiah balance, **Edit Base** pill, and side-by-side current-month **Pemasukan (`+Rp ...`, green)** and **Pengeluaran (`-Rp ...`, red)** summary tiles.
     - **Transaksi Terbaru** header with **`+ Manual`** action button.
     - Scrollable chronological transaction list showing source app badge (DANA, GoPay, Livin', etc.), title, timestamp, and green `+Rp` / red `-Rp` amount. Tapping any row opens an Edit/Delete sheet.
  2. **Stats:**
     - Segmented period switcher: **`Harian` | `Bulanan` | `Tahunan`** plus previous/next period selector (e.g., switch month in Harian, switch year in Bulanan).
     - **Rasio Pemasukan vs Pengeluaran Card:** Donut/ring chart + horizontal split ratio bar showing green `% Masuk` vs red `% Keluar`, total `+Rp` and `-Rp`, and net cashflow pill. Handles zero-data periods gracefully without crash or NaN.
     - **Inflow & Outflow Trend Chart:** Paired green (`+`) and red (`-`) vertical bars:
       - **Harian:** Day-by-day bars across the selected month (scrollable/readable or 7-day / full-month view with tap-to-inspect exact day totals).
       - **Bulanan:** Jan–Dec month-by-month bars for the selected year.
       - **Tahunan:** Year-by-year bars across all years with recorded data.
     - **Ringkasan per Aplikasi Card:** Transaction counts and inflow/outflow totals grouped by app (`DANA`, `GoPay`, `Livin'`, `ShopeePay`, `Manual`, etc.) for the active period.
  3. **Settings:**
     - **Status & Setup OPPO A17 / Android:** Live indicator + one-tap buttons for (a) Notification Access, (b) Battery Optimization ("Allow background activity"), (c) **Keep-Alive Notification** toggle (ON by default — silent low-priority foreground service notification to protect against ColorOS background kills), and (d) ColorOS Auto-Launch & Lock-in-Recents instructions.
     - **Aplikasi Terpantau (Tracked Apps):** Individual ON/OFF toggles for DANA, GoPay/Gojek, Livin' Mandiri, ShopeePay, OVO, BCA, SeaBank.
     - **Notification Inspector (Debug Log):** Shows the last 100 notifications from tracked apps with status badge (`Captured`, `Promo`, `Non-Saldo`, `Duplicate`, `Unparsed`) and full raw text. Tapping an `Unparsed` (or `Non-Saldo`) item opens a pre-filled "Add Transaction" sheet so the user can record it in one tap (and copy the raw text if they want to add a parser rule later).
     - **Tema:** Follow Phone Theme (default) / Dark / Light.
     - **Backup Lokal (My Call — Included for Data Safety):** Export and Import transactions + base balance as a local JSON file via Android's document picker (no storage permissions needed).

---

## 5. High-Level Phased Roadmap

### Phase 1 — v1 Core APK + Charts + GitHub Actions (Current Build Target)
1. **Project Foundation & CI:** Initialize Kotlin + Jetpack Compose + Room Gradle project in `zuraruldev/saldo-tracking-apps`, generate launcher icons from `anime.jpg`, set up stable debug signing keystore, and verify GitHub Actions builds & uploads a downloadable APK artifact.
2. **Data Layer & Balance Engine:** Room database (`transactions`, `notification_logs`), DataStore preferences (base balance, theme, enabled apps), reactive balance calculation & reconciliation logic, unit tests.
3. **Notification Parser & Listener Service:** Pure-Kotlin modular parser registry (DANA, GoPay/Gojek, Livin' Mandiri, ShopeePay, OVO, BCA, SeaBank), promo filter, deduplication window, extensive Indonesian notification unit test suite, and Android `NotificationListenerService` with self-rebind.
4. **Home, Manual CRUD & Settings UI:** Material 3 Light/Dark system theme, Home balance card + Edit Base + transaction feed + Add/Edit/Delete sheet, Settings with OPPO A17 setup checklist, tracked-app toggles, Notification Inspector with 1-tap promote, and JSON export/import.
5. **Stats & Charts Screen:** `Harian | Bulanan | Tahunan` aggregations, Compose Canvas donut ratio chart + split bar, interactive paired green/red bar chart with tap tooltips, per-app summary card, and empty-state handling.
6. **End-to-End Verification & First APK Release:** Full test suite pass, push to GitHub, verify green GitHub Actions run, and document install/setup steps for OPPO A17 in Obsidian.

### Phase 2 — Real-World OPPO A17 Tuning (Post-v1 Iteration)
- Install v1 APK on OPPO A17, do real test transactions on DANA, GoPay, and Livin' Mandiri.
- Inspect any missed/unparsed wording via the built-in **Notification Inspector** and refine regex/keyword rules with real notification fixtures in unit tests.
- Optionally add custom user-defined parser rules in-app if needed.

### Phase 3 — Optional VPS Integration (Future Expansion, Out of v1 Scope)
- Only if desired later: lightweight API on the user's VPS for encrypted backup/sync, web stats dashboard, email receipt ingestion, or daily/weekly Telegram summaries.

---

## 6. What's Left to the Builder

The builder is free to settle these implementation details cleanly without coming back to the user:
- Exact Kotlin, Gradle, AGP, Jetpack Compose BOM, and Room versions (compatible with `minSdk = 26`, `targetSdk = 34`, JDK 17 on `ubuntu-latest` GitHub Actions runners).
- Exact package name (e.g., `dev.zurarul.saldotracking`) and internal Kotlin package/module layout.
- Whether charts are rendered via pure Jetpack Compose `Canvas` (recommended for lightweight OPPO A17 performance and zero external bloat) or a minimal Compose charting library.
- Exact Room SQL schema column names, indices, and DataStore keys.
- Exact crop/inset padding of `anime.jpg` for Android adaptive icon foreground + background color so Azusa and the bills remain centered inside circular/squircle ColorOS icon masks.
- Exact unit test fixtures and Gradle caching setup in `.github/workflows/build-apk.yml`.
