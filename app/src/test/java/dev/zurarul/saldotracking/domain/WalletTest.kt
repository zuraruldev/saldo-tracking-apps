package dev.zurarul.saldotracking.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalletTest {
    @Test
    fun forPackage_mapsKnownPackagesAndReturnsNullForUnknown() {
        assertEquals(Wallet.GOPAY, Wallet.forPackage("com.gojek.app"))
        assertEquals(Wallet.GOPAY, Wallet.forPackage("com.gojek.gopay"))
        assertEquals(Wallet.DANA, Wallet.forPackage("id.dana"))
        assertEquals(Wallet.LIVIN, Wallet.forPackage("id.bmri.livin"))
        assertEquals(Wallet.SHOPEEPAY, Wallet.forPackage("com.shopee.id"))
        assertEquals(Wallet.SHOPEEPAY, Wallet.forPackage("com.shopeepay.id"))
        assertEquals(Wallet.OVO, Wallet.forPackage("ovo.id"))
        assertEquals(Wallet.BCA, Wallet.forPackage("com.bca"))
        assertEquals(Wallet.BCA, Wallet.forPackage("com.bca.mybca.omni.android"))
        assertEquals(Wallet.SEABANK, Wallet.forPackage("id.co.bankbkemobile.digitalbank"))
        assertNull(Wallet.forPackage("com.whatsapp"))
    }

    @Test
    fun requiredKeyword_returnsWalletNameOnlyForSuperApps() {
        assertEquals("gopay", Wallet.requiredKeyword("com.gojek.app"))
        assertEquals("shopeepay", Wallet.requiredKeyword("com.shopee.id"))
        assertNull(Wallet.requiredKeyword("com.gojek.gopay"))
        assertNull(Wallet.requiredKeyword("id.dana"))
    }
}
