package dev.zurarul.saldotracking.domain

const val MANUAL_SOURCE = "Manual"

enum class WalletKind {
    EWALLET,
    BANK
}

enum class Wallet(
    val displayName: String,
    val kind: WalletKind,
    val packages: Set<String>,
    val selfAlias: String?
) {
    DANA("DANA", WalletKind.EWALLET, setOf("id.dana"), "dana"),
    GOPAY("GoPay", WalletKind.EWALLET, setOf("com.gojek.gopay", "com.gojek.app"), "gopay"),
    LIVIN("Livin' Mandiri", WalletKind.BANK, setOf("id.bmri.livin"), null),
    SHOPEEPAY("ShopeePay", WalletKind.EWALLET, setOf("com.shopee.id", "com.shopeepay.id"), "shopeepay"),
    OVO("OVO", WalletKind.EWALLET, setOf("ovo.id"), "ovo"),
    BCA("BCA", WalletKind.BANK, setOf("com.bca", "com.bca.mybca.omni.android"), null),
    SEABANK("SeaBank", WalletKind.BANK, setOf("id.co.bankbkemobile.digitalbank"), null);

    companion object {
        fun forPackage(pkg: String): Wallet? = entries.firstOrNull { pkg in it.packages }

        fun requiredKeyword(pkg: String): String? = when (pkg) {
            "com.gojek.app" -> "gopay"
            "com.shopee.id" -> "shopeepay"
            else -> null
        }
    }
}
