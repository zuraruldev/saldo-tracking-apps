# Saldo Tracking v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A personal Android APK that auto-tracks a combined Rupiah saldo from DANA / GoPay / Livin' Mandiri (+ ShopeePay, OVO, BCA, SeaBank) notifications, with green/red cashflow stats, built and published as a downloadable APK by GitHub Actions.

**Architecture:** Single-module native Android app. All money logic (amount parsing, classification, dedup, balance math, stats aggregation, backup codec, UI-state builders) is pure Kotlin under JVM unit tests; Android layers (Room, DataStore, `NotificationListenerService`, foreground service, Compose UI) are thin shells around it. Aggregation runs in Kotlin over the full transaction list (personal data volume is small), so Room stays a dumb store.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose (BOM 2024.12.01, Material 3), Room 2.6.1 (KSP), DataStore Preferences 1.1.1, kotlinx-serialization-json 1.7.3, Coroutines 1.9.0, JUnit 4.13.2, AGP 8.7.3, Gradle 8.11.1, JDK 17, GitHub Actions.

**Spec:** `/home/zura/Obsidian Vault/antigravity/antigravity project/apps money tracking/2026-11-23-saldo-tracking-design.md` (copied into the repo at `docs/superpowers/specs/2026-11-23-saldo-tracking-design.md` in Task 1).

**Repo:** `https://github.com/zuraruldev/saldo-tracking-apps.git`, cloned to `/home/zura/saldo-tracking-apps`. Kotlin root package `dev.zurarul.saldotracking` → source root `app/src/main/java/dev/zurarul/saldotracking/` (abbreviated `src/` below), tests `app/src/test/java/dev/zurarul/saldotracking/` (abbreviated `test/`).

## Global Constraints

- `applicationId` / `namespace` = `dev.zurarul.saldotracking`; `minSdk = 26`, `targetSdk = 34`, `compileSdk = 35`; Java/Kotlin target 17.
- Pinned versions: AGP `8.7.3`, Gradle wrapper `8.11.1`, Kotlin `2.1.0` (plus `org.jetbrains.kotlin.plugin.compose` and `.serialization` `2.1.0`), KSP `2.1.0-1.0.29`, Compose BOM `2024.12.01`, activity-compose `1.9.3`, lifecycle `2.8.7`, core-ktx `1.15.0`, Room `2.6.1`, DataStore `1.1.1`, serialization-json `1.7.3`, coroutines `1.9.0`, JUnit `4.13.2`. No `material-icons-extended` (APK size); custom vector for anything missing from core icons.
- **No `INTERNET` permission.** All data stays on-device.
- **Data must survive APK updates:** Room DB `version = 1`, never call `fallbackToDestructiveMigration*`. Both `debug` and `release` are signed with the committed keystore `app/saldo-shared.keystore` (alias `saldo`, store + key password `saldotracking`, CN=`Saldo Tracking`). *(Builder call, see design §3: committed, non-secret key is acceptable for a personal sideloaded app.)*
- CI ships the **`release`** variant (`isMinifyEnabled = false`, not debuggable → smooth Compose on the Helio G35). `versionCode = GITHUB_RUN_NUMBER` env (fallback `1`), `versionName = "1.0.<versionCode>"`.
- Colors (dynamic color **off**): income `#22C55E` dark / `#16A34A` light; expense `#F43F5E` dark / `#DC2626` light; background `#0D1117` dark / `#F8FAFC` light; surface `#161B22` dark / `#FFFFFF` light. Theme follows system by default.
- Money format: `Rp 2.450.000`, signed `+Rp 500.000` / `-Rp 28.000`. Green is only ever income, red only ever expense.
- UI copy (exact): tabs `Home` / `Stats` / `Settings`; `Total Saldo`, `Edit Base`, `Pemasukan`, `Pengeluaran`, `Transaksi Terbaru`, `+ Manual`, `Auto-Track ON`, `Auto-Track PAUSED`, `Harian` / `Bulanan` / `Tahunan`, `Rasio Pemasukan vs Pengeluaran`, `Ringkasan per Aplikasi`, `Aplikasi Terpantau`, `Notification Inspector`, `Tema`, `Backup Lokal`.
- Dedup window `15_000` ms on key `(Wallet, amountIdr, TxType)`; notification log capped at `100` rows.
- Runtime timezone = `ZoneId.systemDefault()`; every pure function takes `zone: ZoneId`; tests use `ZoneId.of("Asia/Jakarta")`.
- Local build env for every verification command below:
  `export JAVA_HOME=$HOME/.local/share/jdk-17 ANDROID_HOME=$HOME/Android/Sdk && export PATH=$JAVA_HOME/bin:$PATH && cd /home/zura/saldo-tracking-apps`
  (machine has ~1.7 GB free RAM → `gradle.properties` caps Gradle at `-Xmx1536m`, `org.gradle.workers.max=2`, `kotlin.compiler.execution.strategy=in-process`).

## Review Focus

1. **"Terima kasih" in a payment notification** (e.g. GoPay "Terima kasih! Kamu udah bayar Rp20.000") must be EXPENSE, never INCOME → test in Task 5.
2. **Failed / pending / expired transactions** ("Transaksi gagal", "Menunggu pembayaran") must never move the saldo → `IGNORED_FAILED` tests in Task 5.
3. **Balance-info amounts** ("Sisa saldo Rp 1.250.000. Pembayaran Rp 25.000 berhasil") must yield the transaction amount, not the balance → test in Task 4.
4. **Near-midnight WIB transactions** (23:30 WIB = 16:30 UTC) must land in the local day/month bucket → test in Task 3.
5. **Malformed or foreign backup file** must be rejected before anything is deleted → decode-before-replace contract + codec tests in Task 6.

---

### Task 0: Local toolchain (no repo changes)

- [ ] **Step 1: Install JDK 17 (Temurin) to `$HOME/.local/share/jdk-17`** from `https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse` (tarball, strip top dir).
- [ ] **Step 2: Install Android cmdline-tools** (latest `commandlinetools-linux-*_latest.zip` from `dl.google.com/android/repository/`) to `$HOME/Android/Sdk/cmdline-tools/latest`, then `yes | sdkmanager --licenses` and `sdkmanager "platforms;android-35" "build-tools;34.0.0" "platform-tools"`.
- [ ] **Step 3: Download Gradle `8.11.1` bin zip** to `$HOME/.local/share/gradle-8.11.1` (used once to generate the wrapper).
- [ ] **Step 4: Verify.** Run `java -version` → `17.x`; `$ANDROID_HOME/build-tools/34.0.0/apksigner --version` → prints a version.

---

### Task 1: Project scaffold, icon, signing, CI

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `.gitignore`, `README.md`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro` (empty), `app/saldo-shared.keystore`
- Create: `app/src/main/AndroidManifest.xml`, `src/MainActivity.kt` (placeholder `Text("Saldo Tracking")`)
- Create: `app/src/main/res/values/{strings.xml,themes.xml,colors.xml}`, `values-night/colors.xml`, `mipmap-*/…`, `mipmap-anydpi-v26/{ic_launcher,ic_launcher_round}.xml`, `values/ic_launcher_background.xml`
- Create: `art/anime.jpg` (copy of the Obsidian asset), `tools/make_icons.py`
- Create: `.github/workflows/build-apk.yml`
- Create: `docs/superpowers/specs/2026-11-23-saldo-tracking-design.md`, `docs/superpowers/plans/2026-11-23-saldo-tracking-v1-plan.md` (copies of the Obsidian docs)

**Interfaces:**
- Produces: a building Gradle project every later task adds to; `./gradlew test assembleRelease` as the universal verify command; APK at `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 1: Clone** `git clone https://github.com/zuraruldev/saldo-tracking-apps.git /home/zura/saldo-tracking-apps` (repo is empty; default branch `main`).
- [ ] **Step 2: Write Gradle files** with the pinned versions. `app/build.gradle.kts`: plugins `com.android.application`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`, `com.google.devtools.ksp`; `signingConfigs.create("shared")` → `file("saldo-shared.keystore")`; both build types use it; `ksp { arg("room.generateKotlin", "true") }`; `testOptions.unitTests.isReturnDefaultValues = true`. Declare every library the plan uses now (compose ui/material3/tooling-preview, activity-compose, lifecycle-runtime-compose + viewmodel-compose, core-ktx, room runtime/ktx/compiler, datastore-preferences, serialization-json, coroutines-android; test: junit, coroutines-test). `.gitignore` covers `build/`, `.gradle/`, `local.properties`, `.idea/`, `*.iml`, `.superpowers/`.
- [ ] **Step 3: Generate wrapper:** `$HOME/.local/share/gradle-8.11.1/bin/gradle wrapper --gradle-version 8.11.1`; write `local.properties` with `sdk.dir=$HOME/Android/Sdk` (gitignored).
- [ ] **Step 4: Generate keystore:** `keytool -genkeypair -keystore app/saldo-shared.keystore -alias saldo -keyalg RSA -keysize 2048 -validity 36500 -storepass saldotracking -keypass saldotracking -dname "CN=Saldo Tracking"`.
- [ ] **Step 5: Icons.** `tools/make_icons.py <src.jpg> <res_dir>` (Pillow): center-square crop; legacy `ic_launcher.png` at 48/72/96/144/192 px for m/h/xh/xxh/xxxhdpi and circle-masked `ic_launcher_round.png` at the same sizes; adaptive `ic_launcher_foreground.png` at 108/162/216/324/432 px = transparent canvas with the photo scaled to 76/108 of the side, centered (fully covers the 72 dp safe zone); writes `values/ic_launcher_background.xml` with the average color of the photo's border pixels. Run it on `art/anime.jpg`.
- [ ] **Step 6: Manifest:** `<application android:name` left default for now, `android:icon="@mipmap/ic_launcher"`, `android:roundIcon="@mipmap/ic_launcher_round"`, `android:label="Saldo Tracking"`, theme `@style/Theme.SaldoTracking` (parent `android:Theme.Material.NoActionBar`, `windowBackground` from `colors.xml`/`values-night`), launcher `MainActivity` with `enableEdgeToEdge()`. No permissions yet.
- [ ] **Step 7: CI workflow** `.github/workflows/build-apk.yml`: `on: push (main), pull_request, workflow_dispatch`; `ubuntu-latest`; `actions/checkout@v4` → `actions/setup-java@v4` (temurin 17) → `gradle/actions/setup-gradle@v4` → `./gradlew test assembleRelease` → `actions/upload-artifact@v4` with `name: SaldoTracking-v1.0.${{ github.run_number }}`, `path: app/build/outputs/apk/release/app-release.apk`, `retention-days: 30`.
- [ ] **Step 8: Verify locally.** Run `./gradlew test assembleRelease` → `BUILD SUCCESSFUL`; run `$ANDROID_HOME/build-tools/34.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` → `Signer #1 certificate DN: CN=Saldo Tracking`.
- [ ] **Step 9: Commit + push** `git add -A && git commit -m "chore: scaffold Android project, icon, shared signing, CI APK build" && git push -u origin main`. Verify the Actions run is green and the artifact exists (`https://api.github.com/repos/zuraruldev/saldo-tracking-apps/actions/runs?per_page=1` → `conclusion: success`).

---

### Task 2: Domain model, Indonesian formatting, balance math

**Files:**
- Create: `src/domain/Transaction.kt`, `src/domain/NotificationLog.kt`, `src/domain/Wallet.kt`, `src/domain/IdFormat.kt`, `src/domain/BalanceCalculator.kt`
- Test: `test/domain/IdFormatTest.kt`, `test/domain/BalanceCalculatorTest.kt`, `test/domain/WalletTest.kt`

**Interfaces:**
- Produces:
  - `enum class TxType { INCOME, EXPENSE }`
  - `@Entity(tableName = "transactions", indices = [Index("timestampMillis")]) data class Transaction(@PrimaryKey(autoGenerate = true) val id: Long = 0, val amountIdr: Long, val type: TxType, val sourceApp: String, val sourcePackage: String?, val title: String, val rawNotificationText: String?, val timestampMillis: Long, val isManual: Boolean)`
  - `enum class LogStatus { CAPTURED, IGNORED_PROMO, IGNORED_FAILED, IGNORED_NON_SALDO, IGNORED_DUPLICATE, IGNORED_UNPARSED }`
  - `@Entity(tableName = "notification_logs") data class NotificationLog(@PrimaryKey(autoGenerate = true) val id: Long = 0, val packageName: String, val sourceApp: String, val title: String, val text: String, val postedAtMillis: Long, val status: LogStatus, val parsedAmountIdr: Long?, val parsedType: TxType?)`
  - `enum class WalletKind { EWALLET, BANK }`
  - `enum class Wallet(val displayName: String, val kind: WalletKind, val packages: Set<String>, val selfAlias: String?)` with entries (exact): `DANA("DANA", EWALLET, {"id.dana"}, "dana")`, `GOPAY("GoPay", EWALLET, {"com.gojek.gopay","com.gojek.app"}, "gopay")`, `LIVIN("Livin' Mandiri", BANK, {"id.bmri.livin"}, null)`, `SHOPEEPAY("ShopeePay", EWALLET, {"com.shopee.id","com.shopeepay.id"}, "shopeepay")`, `OVO("OVO", EWALLET, {"ovo.id"}, "ovo")`, `BCA("BCA", BANK, {"com.bca","com.bca.mybca.omni.android"}, null)`, `SEABANK("SeaBank", BANK, {"id.co.bankbkemobile.digitalbank"}, null)`; `companion fun forPackage(pkg: String): Wallet?`; `companion fun requiredKeyword(pkg: String): String?` → `"gopay"` for `com.gojek.app`, `"shopeepay"` for `com.shopee.id`, else `null`.
  - `const val MANUAL_SOURCE = "Manual"` (in `Wallet.kt`)
  - `enum class AmountSign { NONE, PLUS, MINUS }`; `fun formatRupiah(amountIdr: Long, sign: AmountSign = AmountSign.NONE): String`; `fun formatRupiahCompact(amountIdr: Long): String`; `fun monthShortId(month: Int): String` (Jan Feb Mar Apr Mei Jun Jul Agu Sep Okt Nov Des); `fun monthLongId(month: Int): String` (Januari … Desember)
  - `object BalanceCalculator { fun netFlow(txs: List<Transaction>): Long; fun currentSaldo(baseBalanceIdr: Long, txs: List<Transaction>): Long; fun rebaseFor(actualSaldoIdr: Long, txs: List<Transaction>): Long }`

- [ ] **Step 1: Write failing tests.**

```kotlin
// IdFormatTest
assertEquals("Rp 2.450.000", formatRupiah(2_450_000))
assertEquals("+Rp 500.000", formatRupiah(500_000, AmountSign.PLUS))
assertEquals("-Rp 28.000", formatRupiah(28_000, AmountSign.MINUS))
assertEquals("-Rp 150.000", formatRupiah(-150_000))          // negative saldo
assertEquals("Rp 0", formatRupiah(0))
assertEquals("Rp 999", formatRupiahCompact(999))
assertEquals("Rp 1,5rb", formatRupiahCompact(1_500))
assertEquals("Rp 850rb", formatRupiahCompact(850_000))
assertEquals("Rp 1jt", formatRupiahCompact(1_000_000))
assertEquals("Rp 1,72jt", formatRupiahCompact(1_720_000))
assertEquals("Rp 14,9jt", formatRupiahCompact(14_900_000))
assertEquals("Rp 2,5M", formatRupiahCompact(2_500_000_000))
assertEquals("Agu", monthShortId(8)); assertEquals("Desember", monthLongId(12))

// BalanceCalculatorTest (tx(type, amount) helper)
currentSaldo(1_000_000, [INCOME 500_000, EXPENSE 28_000, EXPENSE 101_000]) == 1_371_000
netFlow([EXPENSE 101_000 (Livin'), INCOME 100_000 (DANA)]) == -1_000   // self-transfer fee only
currentSaldo(750_000, []) == 750_000
val base = rebaseFor(2_000_000, txs /* net -1_000 */); base == 2_001_000 && currentSaldo(base, txs) == 2_000_000

// WalletTest
Wallet.forPackage("com.gojek.app") == GOPAY; Wallet.forPackage("com.whatsapp") == null
Wallet.requiredKeyword("com.shopee.id") == "shopeepay"; Wallet.requiredKeyword("id.dana") == null
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest` → FAIL (unresolved references).
- [ ] **Step 3: Implement** the files above. `formatRupiah` uses `.` thousands grouping (`Locale("in","ID")` `DecimalFormat("#,###")` or manual); `formatRupiahCompact` thresholds: `≥1e9 → M`, `≥1e6 → jt`, `≥1e3 → rb`, ≤2 decimals, trailing zeros trimmed, comma decimal separator.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest` → PASS.
- [ ] **Step 5: Commit** `git commit -am "feat: domain model, Rupiah formatting, balance math"` (add new files first).

---

### Task 3: Stats aggregation

**Files:**
- Create: `src/stats/StatsCalculator.kt`
- Test: `test/stats/StatsCalculatorTest.kt`

**Interfaces:**
- Consumes: `Transaction`, `TxType`, `monthShortId`, `monthLongId` (Task 2).
- Produces:
  - `enum class Period { DAILY, MONTHLY, YEARLY }`
  - `data class FlowTotals(val incomeIdr: Long, val expenseIdr: Long) { val netIdr: Long; val incomeShare: Float? /* null when both are 0 */ }`
  - `data class Bucket(val label: String, val incomeIdr: Long, val expenseIdr: Long)`
  - `data class AppSummary(val sourceApp: String, val count: Int, val incomeIdr: Long, val expenseIdr: Long)`
  - `data class StatsSnapshot(val period: Period, val title: String, val totals: FlowTotals, val buckets: List<Bucket>, val perApp: List<AppSummary>)`
  - `object StatsCalculator { fun monthTotals(txs: List<Transaction>, month: YearMonth, zone: ZoneId): FlowTotals; fun snapshot(txs: List<Transaction>, period: Period, anchor: YearMonth, zone: ZoneId, currentYear: Int): StatsSnapshot }`
  - Rules: DAILY → one bucket per day of `anchor` month, labels `"1".."N"`, title `"November 2026"`, totals = that month. MONTHLY → 12 buckets labelled by `monthShortId`, title `"2026"`, totals = `anchor.year`. YEARLY → one bucket per year from `min(earliestTxYear, currentYear)` to `max(latestTxYear, currentYear)`, title `"Semua Tahun"`, totals = all. `perApp` covers the same range, sorted by `count` desc then `sourceApp`.

- [ ] **Step 1: Write failing tests** (`zone = Asia/Jakarta`, helper `at("2026-11-23T23:30")` = local time in zone):

```kotlin
// near-midnight WIB stays on local day (Review Focus 4)
snapshot([INCOME 500_000 at 2026-11-23T23:30], DAILY, YearMonth.of(2026,11), zone, 2026)
  .buckets.single { it.incomeIdr > 0 }.label == "23"
// month boundary
monthTotals([EXPENSE 10_000 at 2026-11-30T23:59, EXPENSE 20_000 at 2026-12-01T00:01], YearMonth.of(2026,11), zone) == FlowTotals(0, 10_000)
// empty period: no NaN, right bucket count
snapshot([], DAILY, YearMonth.of(2026,11), zone, 2026).let { it.totals.incomeShare == null && it.buckets.size == 30 && it.title == "November 2026" }
snapshot([], DAILY, YearMonth.of(2024,2), zone, 2026).buckets.size == 29
// ratio
FlowTotals(3_200_000, 1_480_000).incomeShare!! in 0.6837f..0.6838f; FlowTotals(3_200_000, 1_480_000).netIdr == 1_720_000
// monthly
snapshot(txs in Jan & Nov 2026, MONTHLY, YearMonth.of(2026,11), zone, 2026).buckets.map { it.label } == listOf("Jan","Feb","Mar","Apr","Mei","Jun","Jul","Agu","Sep","Okt","Nov","Des")
// yearly span fills gaps
snapshot([tx 2024-05-01, tx 2026-01-01], YEARLY, YearMonth.of(2026,11), zone, 2026).buckets.map { it.label } == listOf("2024","2025","2026")
// per-app
perApp for [DANA x3, GoPay x1, Livin' x2] == [DANA(3), Livin' Mandiri(2), GoPay(1)] with summed income/expense
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*StatsCalculatorTest'` → FAIL.
- [ ] **Step 3: Implement** with `java.time` (`Instant.ofEpochMilli(ts).atZone(zone)`), half-open ranges `[start, end)`.
- [ ] **Step 4: Run** same command → PASS.
- [ ] **Step 5: Commit** `feat: daily/monthly/yearly cashflow stats`.

---

### Task 4: Rupiah amount extraction

**Files:**
- Create: `src/parser/AmountExtractor.kt`
- Test: `test/parser/AmountExtractorTest.kt`

**Interfaces:**
- Produces: `object AmountExtractor { fun extract(text: String): Long? }` — returns a positive whole-Rupiah amount or `null`.
- Algorithm (fixed by spec values):
  - Match regex `(?i)\b(?:rp|idr)\.?\s*(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,](\d{1,2}))?(?!\d)`; amount = group 1 with `.`/`,` removed; decimals (group 2) truncated.
  - Skip a match when the text before it ends with a balance phrase: `(?i)(sisa saldo|saldo akhir|saldo (anda|kamu|mu)( saat ini| sekarang)?|saldo tersedia|saldo efektif)\s*(:|adalah|sebesar|menjadi)?\s*$`.
  - If any remaining match has `total` (case-insensitive) within the 20 chars before it, return the last such match; otherwise return the first remaining match.
  - Return `null` for `0` or `> 10_000_000_000`.

- [ ] **Step 1: Write failing tests.**

```kotlin
extract("Pembayaran Rp25.000 ke Kopi Kenangan berhasil") == 25_000L
extract("Transfer Rp 1.250.000,00 berhasil") == 1_250_000L
extract("Payment IDR 50,000.00 success") == 50_000L
extract("IDR 50.000 diterima") == 50_000L
extract("Bayar parkir Rp 500") == 500L
extract("Rp 50.000,- telah dibayar") == 50_000L
extract("Transfer Rp 100.000 berhasil. Biaya admin Rp 2.500. Total Rp 102.500") == 102_500L
extract("Sisa saldo Rp 1.250.000. Pembayaran Rp 25.000 berhasil") == 25_000L   // Review Focus 3
extract("Pembayaran Rp 25.000 berhasil. Saldo kamu sekarang Rp 1.225.000") == 25_000L
extract("Saldo DANA kamu bertambah Rp100.000") == 100_000L
extract("Pembayaran berhasil") == null
extract("Rp 0") == null; extract("Rp 0,00") == null
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*AmountExtractorTest'` → FAIL.
- [ ] **Step 3: Implement** per the algorithm above.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat: Indonesian Rupiah amount extraction`.

---

### Task 5: Text rules, dedup, notification processor

**Files:**
- Create: `src/parser/TextRules.kt`, `src/parser/Deduplicator.kt`, `src/parser/NotificationProcessor.kt`
- Test: `test/parser/TextRulesTest.kt`, `test/parser/NotificationProcessorTest.kt`

**Interfaces:**
- Consumes: `Wallet`, `WalletKind`, `TxType`, `LogStatus`, `Transaction`, `NotificationLog` (Task 2); `AmountExtractor.extract` (Task 4).
- Produces:
  - `object TextRules { fun normalize(title: String, text: String): String; fun isPromo(t: String): Boolean; fun isFailedOrPending(t: String): Boolean; fun isNonSaldo(t: String): Boolean; fun classify(t: String, wallet: Wallet): TxType? }` — all take the normalized string.
  - `class Deduplicator(private val windowMillis: Long = 15_000) { @Synchronized fun isDuplicate(wallet: Wallet, amountIdr: Long, type: TxType, atMillis: Long): Boolean }` (records the key when not a duplicate)
  - `data class RawNotification(val packageName: String, val title: String, val text: String, val postedAtMillis: Long, val isOngoing: Boolean = false, val isGroupSummary: Boolean = false)`
  - `data class ProcessOutcome(val log: NotificationLog, val transaction: Transaction?)`
  - `class NotificationProcessor(private val deduplicator: Deduplicator = Deduplicator()) { fun process(raw: RawNotification, enabledWallets: Set<Wallet>): ProcessOutcome? }`
- Keyword values (exact; "word" = regex `\b…\b` match, otherwise substring):
  - `normalize`: lowercase `"$title $text"`, collapse whitespace, remove the phrases `terima kasih` and `thank you`.
  - Promo strong (substring): `hingga`, `s.d.`, `s/d`, `kode otp`, `kode verifikasi`, `klaim`, `dapatkan`, `voucher`, `promo`, `diskon`, `flash sale`, `kupon`, `undian`, `menangkan`, `berlaku sampai`, `buruan`; plus word `otp`. Promo weak: `cashback`, `gratis`, `hadiah`. Completion: `berhasil`, `sukses`, `telah masuk`, `diterima`, `bertambah`. `isPromo = strong || (weak && !completion)`.
  - Failed/pending (substring): `gagal`, `dibatalkan`, `failed`, `pending`, `menunggu`, `sedang diproses`, `sedang dalam proses`, `kedaluwarsa`, `expired`, `ditolak`.
  - Non-saldo substring: `paylater`, `pay later`, `cicil`, `kartu kredit`, `credit card`; word: `coins`, `koin`, `poin`, `points`, `goals`, `tabungan`, `deposito`, `reksa dana`, `reksadana`, `investasi`, `emas`.
  - `classify` order: (1) top-up regex `\b(top ?up|isi saldo|isi ulang saldo)\b\s*([a-z-]+)?` → `BANK` ⇒ EXPENSE; `EWALLET` ⇒ EXPENSE if the next word is in {`gopay`,`ovo`,`shopeepay`,`dana`,`linkaja`,`flazz`,`e-money`,`emoney`,`brizzi`,`tapcash`,`jakcard`,`isaku`,`sakuku`} minus `wallet.selfAlias`, else INCOME. (2) Income substring: `diterima`, `menerima`, `dana masuk`, `uang masuk`, `transfer masuk`, `saldo masuk`, `masuk ke saldo`, `bertambah`, `refund`, `pengembalian dana`, `kamu dapat`, `anda mendapatkan`, `received`, `incoming`; word `kredit` ⇒ INCOME. (3) Expense substring: `pembayaran`, `bayar`, `transfer ke`, `kirim`, `terkirim`, `pembelian`, `beli`, `tarik tunai`, `penarikan`, `berkurang`, `dipotong`, `terdebet`, `qris`, `paid`, `payment`, `sent`; word `debit` ⇒ EXPENSE. (4) else `null`.
  - `process` order: wallet = `Wallet.forPackage` → return `null` if no wallet, wallet not in `enabledWallets`, `isOngoing`, `isGroupSummary`, or blank title+text. Then `IGNORED_PROMO` → `IGNORED_FAILED` → `IGNORED_NON_SALDO` (non-saldo keywords, or `Wallet.requiredKeyword(pkg)` absent from text) → `IGNORED_UNPARSED` (no amount) → `IGNORED_UNPARSED` with `parsedAmountIdr` set (no direction) → `IGNORED_DUPLICATE` → `CAPTURED`. Captured `Transaction`: `sourceApp = wallet.displayName`, `sourcePackage = pkg`, `title = title.ifBlank { text.take(60) }`, `rawNotificationText = "$title\n$text"`, `timestampMillis = postedAtMillis`, `isManual = false`. Log `sourceApp = wallet.displayName`, `parsedType` set only when classified.

- [ ] **Step 1: Write failing tests.**

```kotlin
// TextRulesTest (n = TextRules.normalize(title, text))
isPromo(n("DANA", "Cashback hingga Rp50.000! Bayar pakai DANA sekarang")) == true
isPromo(n("", "Kode OTP kamu 123456. Jangan berikan ke siapa pun")) == true
isPromo(n("", "Cashback Rp2.000 telah masuk ke saldo DANA kamu")) == false
isFailedOrPending(n("Transaksi gagal", "Pembayaran Rp 50.000 tidak dapat diproses")) == true   // Review Focus 2
isFailedOrPending(n("", "Menunggu pembayaran Rp 75.000")) == true
isNonSaldo(n("GoPayLater", "Pembayaran Rp 120.000 berhasil")) == true
isNonSaldo(n("", "Tagihan Kartu Kredit Mandiri Rp 1.500.000")) == true
isNonSaldo(n("", "Rp 50.000 berhasil dipindahkan ke DANA Goals")) == true
isNonSaldo(n("", "Pemasukan Rp 50.000")) == false                // 'emas' whole-word guard
classify(n("Pembayaran Berhasil", "Pembayaran Rp25.000 ke Kopi Kenangan berhasil"), DANA) == EXPENSE
classify(n("Dana Masuk", "Kamu menerima Rp50.000 dari JOHN"), DANA) == INCOME
classify(n("Isi Saldo Berhasil", "Saldo DANA kamu telah bertambah Rp100.000"), DANA) == INCOME
classify(n("Top Up GoPay berhasil", "Rp 50.000"), DANA) == EXPENSE
classify(n("Transaksi Berhasil", "Top Up DANA Rp 101.000 berhasil"), LIVIN) == EXPENSE
classify(n("Transfer masuk", "Rp 500.000 dari BUDI"), LIVIN) == INCOME
classify(n("GoPay", "Terima kasih! Kamu udah bayar Rp20.000 ke Warung Bu Sri"), GOPAY) == EXPENSE  // Review Focus 1
classify(n("GoPay", "Top up GoPay Rp100.000 berhasil"), GOPAY) == INCOME
classify(n("Info", "Rp 10.000"), DANA) == null

// NotificationProcessorTest (fresh processor per test; enabled = Wallet.entries.toSet())
process(raw("com.whatsapp", …), enabled) == null
process(raw("id.dana", …), enabled - DANA) == null
process(raw("id.dana", …, isOngoing = true), enabled) == null
process(raw("id.dana","Pembayaran Berhasil","Pembayaran Rp25.000 ke Kopi Kenangan berhasil", t0), enabled)!!
  .let { it.log.status == CAPTURED && it.transaction!!.type == EXPENSE && it.transaction.amountIdr == 25_000L && it.transaction.sourceApp == "DANA" }
promo DANA text → status IGNORED_PROMO, transaction == null
"Transaksi gagal …" → IGNORED_FAILED
raw("com.gojek.app","GoFood","Pembayaran Rp 42.500 untuk pesananmu berhasil") → IGNORED_NON_SALDO
raw("com.gojek.app","GoPay","Pembayaran GoPay Rp 42.500 berhasil") → CAPTURED, sourceApp "GoPay"
raw("com.shopee.id","Pesanan Dibayar","Pembayaran Rp 89.000 untuk pesanan 2311ABC berhasil") → IGNORED_NON_SALDO
"Info akun kamu" (no amount) → IGNORED_UNPARSED, parsedAmountIdr == null
"Notifikasi Rp 10.000" → IGNORED_UNPARSED, parsedAmountIdr == 10_000L
same DANA payment at t0 and t0+5_000 → second IGNORED_DUPLICATE; at t0+20_000 → CAPTURED
raw("com.gojek.app", "GoPay", "Pembayaran GoPay Rp 20.000 berhasil", t0) then raw("com.gojek.gopay", "GoPay", "Kamu udah bayar Rp20.000", t0+3_000) → second IGNORED_DUPLICATE
Livin' "Top Up DANA Rp 101.000 berhasil" + DANA "Isi Saldo Berhasil … bertambah Rp100.000" at t0 → both CAPTURED (EXPENSE 101_000, INCOME 100_000)
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*TextRulesTest' --tests '*NotificationProcessorTest'` → FAIL.
- [ ] **Step 3: Implement** the three files per the interface values.
- [ ] **Step 4: Run** → PASS (also re-run full `./gradlew testDebugUnitTest`).
- [ ] **Step 5: Commit** `feat: notification classification pipeline with promo/failed/non-saldo guards and dedup`.

---

### Task 6: Persistence, settings, backup codec

**Files:**
- Create: `src/data/SaldoDatabase.kt`, `src/data/TransactionDao.kt`, `src/data/NotificationLogDao.kt`, `src/data/SettingsRepository.kt`, `src/data/BackupCodec.kt`, `src/data/SaldoRepository.kt`, `src/AppContainer.kt`, `src/SaldoApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml` (`android:name=".SaldoApplication"`)
- Test: `test/data/BackupCodecTest.kt`

**Interfaces:**
- Consumes: Task 2 entities, `BalanceCalculator.rebaseFor`, `Wallet`; `ProcessOutcome`, `NotificationProcessor` (Task 5).
- Produces:
  - `@Database(entities = [Transaction::class, NotificationLog::class], version = 1, exportSchema = false) abstract class SaldoDatabase : RoomDatabase { abstract fun transactionDao(); abstract fun notificationLogDao(); companion fun create(context: Context): SaldoDatabase /* name "saldo.db" */ }`
  - `TransactionDao`: `observeAll(): Flow<List<Transaction>>` (ORDER BY timestampMillis DESC), `suspend getAll()`, `suspend insert(tx): Long`, `suspend update(tx)`, `suspend delete(tx)`, `suspend deleteAll()`, `suspend insertAll(txs)`
  - `NotificationLogDao`: `observeRecent(limit: Int = 100): Flow<List<NotificationLog>>` (ORDER BY postedAtMillis DESC), `suspend insert(log)`, `suspend trimTo(keep: Int = 100)` = `DELETE FROM notification_logs WHERE id NOT IN (SELECT id FROM notification_logs ORDER BY postedAtMillis DESC LIMIT :keep)`
  - `enum class ThemeMode { SYSTEM, DARK, LIGHT }`; `data class AppSettings(val baseBalanceIdr: Long = 0, val onboardingDone: Boolean = false, val themeMode: ThemeMode = ThemeMode.SYSTEM, val keepAliveEnabled: Boolean = true, val disabledWallets: Set<Wallet> = emptySet()) { val enabledWallets: Set<Wallet> }`
  - `class SettingsRepository(private val store: DataStore<Preferences>) { val settings: Flow<AppSettings>; suspend fun setBaseBalance(v: Long); suspend fun setOnboardingDone(); suspend fun setThemeMode(m: ThemeMode); suspend fun setKeepAlive(enabled: Boolean); suspend fun setWalletEnabled(w: Wallet, enabled: Boolean) }` (DataStore file `"settings"`; disabled wallets stored as `Set<String>` of enum names so new wallets default ON)
  - `@Serializable data class BackupTransaction(val amountIdr: Long, val type: String, val sourceApp: String, val sourcePackage: String? = null, val title: String, val rawNotificationText: String? = null, val timestampMillis: Long, val isManual: Boolean)`; `@Serializable data class BackupData(val version: Int = 1, val baseBalanceIdr: Long, val transactions: List<BackupTransaction>)`; `class BackupFormatException(msg: String) : Exception(msg)`; `object BackupCodec { fun encode(data: BackupData): String; fun decode(json: String): BackupData /* throws BackupFormatException */; fun fromTransactions(base: Long, txs: List<Transaction>): BackupData; fun toTransactions(data: BackupData): List<Transaction> /* ids = 0 */ }`
  - `class SaldoRepository(db: SaldoDatabase, settings: SettingsRepository) { val transactions: Flow<List<Transaction>>; val recentLogs: Flow<List<NotificationLog>>; suspend fun record(outcome: ProcessOutcome); suspend fun add(tx: Transaction); suspend fun update(tx: Transaction); suspend fun delete(tx: Transaction); suspend fun reconcileTo(actualSaldoIdr: Long); suspend fun exportJson(): String; fun prepareImport(json: String): BackupData; suspend fun applyImport(data: BackupData) }` — `record` inserts tx (if any) + log then `trimTo(100)`; `applyImport` runs `deleteAll` + `insertAll` inside `db.withTransaction`, then `setBaseBalance`. **Contract (Review Focus 5):** UI calls `prepareImport` (validates, no writes) and only then `applyImport`.
  - `class AppContainer(context: Context) { val database; val settingsRepository; val repository: SaldoRepository; val processor: NotificationProcessor }`; `class SaldoApplication : Application() { lateinit var container: AppContainer }`

- [ ] **Step 1: Write failing tests** (`BackupCodecTest`):

```kotlin
val txs = listOf(tx(id=7, INCOME, 500_000, "Livin' Mandiri"), tx(id=9, EXPENSE, 28_000, "DANA", raw="QRIS\nRp28.000"))
val back = BackupCodec.toTransactions(BackupCodec.decode(BackupCodec.encode(BackupCodec.fromTransactions(1_000_000, txs))))
back == txs.map { it.copy(id = 0) }
BackupCodec.decode(BackupCodec.encode(...)).baseBalanceIdr == 1_000_000
assertThrows<BackupFormatException> { decode("not json") }
assertThrows<BackupFormatException> { decode("{}") }
assertThrows<BackupFormatException> { decode("""{"version":2,"baseBalanceIdr":0,"transactions":[]}""") }
assertThrows<BackupFormatException> { decode(json with amountIdr = 0) }
assertThrows<BackupFormatException> { decode(json with type = "FOO") }
decode(valid json + unknown field "extra":1) succeeds (ignoreUnknownKeys)
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*BackupCodecTest'` → FAIL.
- [ ] **Step 3: Implement** all files (`Json { ignoreUnknownKeys = true; prettyPrint = true }`; wrap `SerializationException`/`IllegalArgumentException` into `BackupFormatException`).
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest assembleRelease` → PASS + BUILD SUCCESSFUL (proves Room/KSP codegen compiles). Confirm `grep -r fallbackToDestructive app/src` prints nothing.
- [ ] **Step 5: Commit** `feat: Room storage, DataStore settings, JSON backup codec`.

---

### Task 7: Notification listener, keep-alive service, boot receiver

**Files:**
- Create: `src/service/ListenerStatus.kt`, `src/service/SaldoNotificationListener.kt`, `src/service/KeepAliveService.kt`, `src/service/BootReceiver.kt`, `app/src/main/res/drawable/ic_stat_saldo.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `AppContainer` (`processor`, `repository`, `settingsRepository`), `RawNotification` (Task 5).
- Produces:
  - `object ListenerStatus { val connected: StateFlow<Boolean>; fun set(value: Boolean); fun isAccessGranted(context: Context): Boolean /* NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName) */; fun requestRebind(context: Context) }`
  - `KeepAliveService.start(context: Context)` / `KeepAliveService.stop(context: Context)` — `start` swallows `IllegalStateException` (covers Android 12+ `ForegroundServiceStartNotAllowedException`) and logs.
- Behavior:
  - Listener: `onNotificationPosted` → `RawNotification` from `sbn.packageName`, `EXTRA_TITLE`, `EXTRA_BIG_TEXT ?: EXTRA_TEXT`, `FLAG_ONGOING_EVENT`, `FLAG_GROUP_SUMMARY`, `sbn.postTime` → on a service-scoped `CoroutineScope(SupervisorJob() + Dispatchers.IO)`: read `settings.first().enabledWallets`, `processor.process(...)?.let { repository.record(it) }`. `onListenerConnected` → `ListenerStatus.set(true)`, start keep-alive if enabled. `onListenerDisconnected` → `set(false)` + `requestRebind(ComponentName(this, SaldoNotificationListener::class.java))`. Cancel scope in `onDestroy`.
  - Keep-alive: channel `keep_alive` ("Auto-Track", `IMPORTANCE_MIN`, no sound/vibration, no badge); ongoing notification title `Saldo Tracking`, text `Auto-Track aktif`, small icon `ic_stat_saldo`, tap opens `MainActivity`; `ServiceCompat.startForeground(this, 1001, n, if (SDK_INT >= 34) FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)`; `START_STICKY`.
  - Boot receiver: `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED` → `goAsync()`, read settings, `KeepAliveService.start` if `keepAliveEnabled`.
  - Manifest: permissions `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; listener service `exported="true"`, `permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"`, intent-filter `android.service.notification.NotificationListenerService`; keep-alive service `foregroundServiceType="specialUse"` with `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Keeps notification-based balance tracking alive on aggressive OEM battery managers"/>`; receiver `exported="false"`... **except** `BOOT_COMPLETED` requires `exported="true"` → use `exported="true"` with only those two system actions.

- [ ] **Step 1: Implement** the files and manifest entries above (Android-framework glue; logic is already covered by Task 5 tests).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest assembleRelease` → BUILD SUCCESSFUL; `aapt2 dump xmltree --file AndroidManifest.xml app/build/outputs/apk/release/app-release.apk | grep -E "NotificationListenerService|specialUse|BOOT_COMPLETED"` → all three present; `grep -c INTERNET` on the same dump → `0`.
- [ ] **Step 3: Commit** `feat: notification listener service with keep-alive foreground service and boot restart`.

---

### Task 8: Theme, app shell, onboarding, Home, transaction sheet

**Files:**
- Create: `src/ui/theme/Color.kt`, `src/ui/theme/Theme.kt`, `src/ui/SaldoAppRoot.kt`, `src/ui/onboarding/OnboardingScreen.kt`, `src/ui/home/HomeUiState.kt`, `src/ui/home/HomeViewModel.kt`, `src/ui/home/HomeScreen.kt`, `src/ui/home/TransactionDraft.kt`, `src/ui/home/TransactionSheet.kt`, `app/src/main/res/drawable/ic_tab_stats.xml`
- Modify: `src/MainActivity.kt`
- Test: `test/ui/home/HomeUiStateTest.kt`, `test/ui/home/TransactionDraftTest.kt`

**Interfaces:**
- Consumes: `AppSettings`, `ThemeMode`, `SaldoRepository`, `SettingsRepository` (Task 6); `ListenerStatus` (Task 7); `BalanceCalculator`, `formatRupiah`, `monthShortId`, `MANUAL_SOURCE`, `Wallet` (Task 2); `StatsCalculator.monthTotals` (Task 3).
- Produces:
  - `@Composable fun SaldoTheme(themeMode: ThemeMode, content: @Composable () -> Unit)` + `val LocalCashflowColors` exposing `income: Color`, `expense: Color` (Global Constraints values).
  - `data class TransactionRow(val id: Long, val title: String, val sourceApp: String, val timeLabel: String, val amountText: String, val isIncome: Boolean)`
  - `data class HomeUiState(val saldoText: String, val monthIncomeText: String, val monthExpenseText: String, val rows: List<TransactionRow>, val autoTrackOn: Boolean)`
  - `fun buildHomeUiState(settings: AppSettings, txs: List<Transaction>, autoTrackOn: Boolean, now: Instant, zone: ZoneId): HomeUiState`
  - `fun timeLabel(ts: Long, now: Instant, zone: ZoneId): String` → `"Hari ini · 09:14"`, `"Kemarin · 19:20"`, `"21 Nov · 08:00"`, other year `"21 Nov 2025 · 08:00"`
  - `data class TransactionDraft(val id: Long = 0, val amountInput: String = "", val type: TxType = TxType.EXPENSE, val sourceApp: String = MANUAL_SOURCE, val title: String = "", val timestampMillis: Long)`; `fun parseAmountInput(input: String): Long?` (strip `.`, `,`, spaces; digits only; 1..12 digits; `0` → null); `fun TransactionDraft.toTransaction(original: Transaction?): Transaction?` (null when amount invalid or title blank; keeps original `sourcePackage`/`rawNotificationText`; `isManual = original?.isManual ?: true`); `fun Transaction.toDraft(): TransactionDraft`
  - `@Composable fun TransactionSheet(initial: TransactionDraft, onSave: (TransactionDraft) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit)` — reused by Task 9.
  - `HomeViewModel(container)`: `uiState: StateFlow<HomeUiState>` combining settings, transactions, `ListenerStatus.connected`, and an access-granted flag refreshed via `fun refreshAccess()` on resume; `fun save(draft)`, `fun delete(id)`, `fun reconcile(actualSaldoIdr: Long)`.
- Shell: `SaldoAppRoot` shows `OnboardingScreen` until `onboardingDone` (base-balance input, `Izinkan Akses Notifikasi` button → `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`, `Lanjut` → `setBaseBalance` + `setOnboardingDone`), then a `Scaffold` + `NavigationBar` with `Home` (`Icons.Filled.Home`), `Stats` (`ic_tab_stats`), `Settings` (`Icons.Filled.Settings`); Stats/Settings tabs render placeholders until Tasks 9–10. Home layout per design §4.3 / mockup: status pill (tap `PAUSED` → `ListenerStatus.requestRebind` + switch to Settings tab), hero `Total Saldo` card with `Edit Base` (dialog: enter actual saldo → `reconcile`), month `Pemasukan`/`Pengeluaran` tiles, `Transaksi Terbaru` + `+ Manual`, rows open `TransactionSheet`. Sheet: amount field (number keyboard), `+ Masuk` / `- Keluar` toggle, source dropdown (`Wallet.entries.displayName` + `Manual`), title, M3 date + time pickers, Save / Delete.

- [ ] **Step 1: Write failing tests.**

```kotlin
// HomeUiStateTest (zone Asia/Jakarta, now = 2026-11-23T10:00 local)
buildHomeUiState(AppSettings(baseBalanceIdr = 1_000_000), [INCOME 500_000 @2026-11-23T08:02, EXPENSE 28_000 @2026-11-23T09:14, EXPENSE 200_000 @2026-10-15T12:00], true, now, zone)
  .let { it.saldoText == "Rp 1.272.000" && it.monthIncomeText == "+Rp 500.000" && it.monthExpenseText == "-Rp 28.000" }
rows sorted newest first; rows[0].amountText == "-Rp 28.000" && !rows[0].isIncome; rows[1].isIncome
timeLabel(2026-11-23T09:14) == "Hari ini · 09:14"; timeLabel(2026-11-22T19:20) == "Kemarin · 19:20"
timeLabel(2026-11-21T08:00) == "21 Nov · 08:00"; timeLabel(2025-11-21T08:00) == "21 Nov 2025 · 08:00"

// TransactionDraftTest
parseAmountInput("25.000") == 25_000L; parseAmountInput("") == null; parseAmountInput("0") == null
parseAmountInput("12a") == null; parseAmountInput("1234567890123") == null
TransactionDraft(amountInput = "50000", title = "", timestampMillis = t).toTransaction(null) == null
TransactionDraft(amountInput = "50.000", type = INCOME, title = "Gaji", timestampMillis = t).toTransaction(null)!!
  .let { it.amountIdr == 50_000L && it.isManual && it.sourceApp == "Manual" }
captured.toDraft().copy(amountInput = "30000").toTransaction(captured)!!.let { it.id == captured.id && !it.isManual && it.rawNotificationText == captured.rawNotificationText }
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*HomeUiStateTest' --tests '*TransactionDraftTest'` → FAIL.
- [ ] **Step 3: Implement** builders, then theme, shell, onboarding, Home, sheet, ViewModel (factory via `viewModelFactory { initializer { HomeViewModel((application as SaldoApplication).container) } }`). `MainActivity` sets content `SaldoTheme(settings.themeMode) { SaldoAppRoot() }` and starts `KeepAliveService` in `onResume` when enabled.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest assembleRelease` → PASS + BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat: Home screen, onboarding, manual transaction add/edit/delete, Edit Base`.

---

### Task 9: Settings — setup, tracked apps, inspector, theme, backup

**Files:**
- Create: `src/ui/settings/SettingsViewModel.kt`, `src/ui/settings/SettingsScreen.kt`, `src/ui/settings/InspectorScreen.kt`, `src/ui/settings/InspectorModel.kt`, `src/ui/settings/SetupActions.kt`
- Modify: `src/ui/SaldoAppRoot.kt` (Settings tab)
- Test: `test/ui/settings/InspectorModelTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository`, `SaldoRepository.prepareImport/applyImport/exportJson/add`, `BackupFormatException` (Task 6); `ListenerStatus`, `KeepAliveService` (Task 7); `TransactionSheet`, `TransactionDraft` (Task 8).
- Produces:
  - `fun badgeLabel(status: LogStatus): String` → `CAPTURED "Captured"`, `IGNORED_PROMO "Promo"`, `IGNORED_FAILED "Gagal/Pending"`, `IGNORED_NON_SALDO "Non-Saldo"`, `IGNORED_DUPLICATE "Duplicate"`, `IGNORED_UNPARSED "Unparsed"`
  - `fun NotificationLog.canPromote(): Boolean` → true for every status except `CAPTURED`
  - `fun NotificationLog.toDraft(): TransactionDraft` → `amountInput = parsedAmountIdr?.toString() ?: ""`, `type = parsedType ?: EXPENSE`, `sourceApp`, `title = title.ifBlank { text.take(60) }`, `timestampMillis = postedAtMillis`
  - `object SetupActions { fun openNotificationAccess(ctx); fun requestIgnoreBatteryOptimizations(ctx) /* ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, fallback ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS */; fun openAppDetails(ctx) }`
- Screen sections (in order): **Status & Setup** — live status row, buttons for Notification Access, Battery Optimization, App Info, `Keep-Alive Notification` switch (on API 33+ request `POST_NOTIFICATIONS` first; start/stop service), static ColorOS 12.1 text: *Settings › Battery › App battery management › Saldo Tracking → Allow background activity + Allow auto launch; open Recents → tap the app's ⋮ → Lock*; plus note *Android 13+: if Notification Access is greyed out, App info › ⋮ › Allow restricted settings*. **Aplikasi Terpantau** — one switch per `Wallet`. **Notification Inspector** — row opening `InspectorScreen` (list newest first: badge, app, time, title, full text; promote button opens `TransactionSheet` with `toDraft()`, saving calls `add` with `isManual = true`, `rawNotificationText` = log text). **Tema** — `Ikuti Sistem` / `Gelap` / `Terang`. **Backup Lokal** — Export via `CreateDocument("application/json")` (default name `saldo-backup-YYYYMMDD.json`), Import via `OpenDocument(arrayOf("application/json","text/plain","*/*"))` → `prepareImport` → confirm dialog showing transaction count → `applyImport`; `BackupFormatException` → snackbar `File backup tidak valid`.

- [ ] **Step 1: Write failing tests.**

```kotlin
badgeLabel(IGNORED_FAILED) == "Gagal/Pending"; badgeLabel(IGNORED_NON_SALDO) == "Non-Saldo"
log(CAPTURED).canPromote() == false; log(IGNORED_UNPARSED).canPromote() == true
log(IGNORED_UNPARSED, parsedAmountIdr = 10_000, parsedType = null, title = "").toDraft()
  .let { it.amountInput == "10000" && it.type == EXPENSE && it.title == log.text.take(60) && it.timestampMillis == log.postedAtMillis }
log(IGNORED_UNPARSED, parsedAmountIdr = null).toDraft().amountInput == ""
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*InspectorModelTest'` → FAIL.
- [ ] **Step 3: Implement** model, actions, ViewModel, screens; wire the Settings tab and the Home `PAUSED` pill.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest assembleRelease` → PASS + BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat: Settings with OPPO setup guide, tracked-app toggles, notification inspector, theme, JSON backup`.

---

### Task 10: Stats screen and charts

**Files:**
- Create: `src/ui/stats/StatsUiState.kt`, `src/ui/stats/StatsViewModel.kt`, `src/ui/stats/StatsScreen.kt`, `src/ui/stats/Charts.kt`
- Modify: `src/ui/SaldoAppRoot.kt` (Stats tab)
- Test: `test/ui/stats/StatsUiStateTest.kt`

**Interfaces:**
- Consumes: `StatsCalculator`, `Period`, `Bucket`, `AppSummary` (Task 3); `formatRupiah`, `formatRupiahCompact`, `monthShortId` (Task 2); `LocalCashflowColors` (Task 8).
- Produces:
  - `data class AppSummaryRow(val sourceApp: String, val countText: String /* "14 tx" */, val incomeText: String, val expenseText: String)`
  - `data class StatsUiState(val period: Period, val anchor: YearMonth, val title: String, val canGoPrev: Boolean, val canGoNext: Boolean, val incomeText: String, val expenseText: String, val netText: String, val netPositive: Boolean, val incomeShare: Float?, val ratioLabel: String, val buckets: List<Bucket>, val perApp: List<AppSummaryRow>)`
  - `fun buildStatsUiState(txs: List<Transaction>, period: Period, anchor: YearMonth, zone: ZoneId, today: LocalDate): StatsUiState`
  - `fun shiftAnchor(period: Period, anchor: YearMonth, step: Int): YearMonth` (DAILY ±months, MONTHLY ±years, YEARLY unchanged)
  - `fun bucketDetail(period: Period, anchor: YearMonth, bucket: Bucket): String` → DAILY `"23 Nov: +Rp 500.000 / -Rp 28.000"`, MONTHLY `"Nov 2026: …"`, YEARLY `"2026: …"`
  - Composables: `RatioDonut(incomeShare: Float?)`, `RatioBar(incomeShare: Float?)`, `CashflowBarChart(buckets: List<Bucket>, selectedIndex: Int?, onSelect: (Int) -> Unit)` — pure Compose `Canvas`; paired green/red bars scaled to the period max; horizontally scrollable when `buckets.size > 12` (28 dp per group); tap selects a bucket (`detectTapGestures`), detail text shown under the chart via `bucketDetail`.
- Rules: `canGoNext = false` when the next anchor is after `today`'s month (DAILY) / year (MONTHLY); YEARLY → both false. `canGoPrev` true for DAILY/MONTHLY. `netText` = `"Net +Rp 1,72jt"` / `"Net -Rp 300rb"` (compact). `ratioLabel` = `"68% masuk · 32% keluar"` (rounded), or `"Belum ada data"` when `incomeShare == null`.
- Screen per design §4.3 / mockup: `Harian | Bulanan | Tahunan` segmented buttons, `‹ title ›` selector, ratio card (donut + legend + bar + net pill), trend chart card, `Ringkasan per Aplikasi` card. `StatsViewModel` holds `period` + `anchor` (default `DAILY`, current month).

- [ ] **Step 1: Write failing tests.**

```kotlin
// today = 2026-11-23, zone Asia/Jakarta
buildStatsUiState(txs(income 3_200_000, expense 1_480_000 in Nov 2026), DAILY, YearMonth.of(2026,11), zone, today)
  .let { it.ratioLabel == "68% masuk · 32% keluar" && it.netText == "Net +Rp 1,72jt" && it.netPositive && !it.canGoNext && it.canGoPrev && it.title == "November 2026" }
buildStatsUiState([], DAILY, YearMonth.of(2026,11), zone, today).let { it.ratioLabel == "Belum ada data" && it.incomeShare == null && it.netText == "Net Rp 0" }
buildStatsUiState(txs(expense > income), MONTHLY, YearMonth.of(2026,11), zone, today).netPositive == false
buildStatsUiState(txs, DAILY, YearMonth.of(2026,10), zone, today).canGoNext == true
buildStatsUiState(txs, YEARLY, YearMonth.of(2026,11), zone, today).let { !it.canGoPrev && !it.canGoNext }
shiftAnchor(DAILY, YearMonth.of(2026,1), -1) == YearMonth.of(2025,12); shiftAnchor(MONTHLY, YearMonth.of(2026,11), 1) == YearMonth.of(2027,11)
bucketDetail(DAILY, YearMonth.of(2026,11), Bucket("23", 500_000, 28_000)) == "23 Nov: +Rp 500.000 / -Rp 28.000"
perApp row: AppSummaryRow("DANA", "3 tx", "+Rp …", "-Rp …")
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*StatsUiStateTest'` → FAIL.
- [ ] **Step 3: Implement** builder, ViewModel, charts, screen; wire Stats tab.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest assembleRelease` → PASS + BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat: Stats screen with ratio donut and daily/monthly/yearly cashflow charts`.

---

### Task 11: Release v1 through CI + docs

**Files:**
- Modify: `README.md` (what it is, download from Actions, install + OPPO setup steps, how to report a missed notification via Inspector)
- Create (Obsidian): `/home/zura/Obsidian Vault/antigravity/antigravity project/apps money tracking/Install & Setup OPPO A17.md`, `Roadmap.md` (Phase 1/2/3 checklist from design §5, Phase 1 ticked)

- [ ] **Step 1: Full verify.** `./gradlew clean testDebugUnitTest assembleRelease` → BUILD SUCCESSFUL, 0 failing tests; `apksigner verify --print-certs` → `CN=Saldo Tracking`.
- [ ] **Step 2: Push** `git push origin main`; poll `https://api.github.com/repos/zuraruldev/saldo-tracking-apps/actions/runs?per_page=1` until `status: completed` → expect `conclusion: success` and an artifact `SaldoTracking-v1.0.<n>` at `/actions/runs/<id>/artifacts`.
- [ ] **Step 3: Write docs** (README + the two Obsidian notes). Install note covers: download artifact zip from Actions → extract `app-release.apk` → install (allow "Install unknown apps" for the browser/file manager) → onboarding → Notification Access → Battery: Allow background activity + auto launch → Lock in Recents → make one small DANA/GoPay/Livin' transaction and confirm it appears; if not, check `Notification Inspector`.
- [ ] **Step 4: Commit + push** `docs: install guide and roadmap` → CI green again.
- [ ] **Step 5 (on phone, by user, optional with `adb`):** if the OPPO A17 is connected with USB debugging, `adb install -r app-release.apk` and then a real transaction per primary app; any `Unparsed` / wrong-direction entry in the Inspector becomes a Phase 2 parser fixture.
