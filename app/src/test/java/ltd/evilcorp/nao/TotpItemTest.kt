package ltd.evilcorp.nao

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TotpItemTest {
    @Test
    fun fromUrl_validUrl_returnsTotpItem() {
        val url =
            "otpauth://totp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&period=30"
        val uri = Uri.parse(url)
        val item = TotpItem.fromUrl(uri)

        assertEquals("Example", item?.name)
        assertEquals("user@example.com", item?.extraInfo)
        assertEquals("JBSWY3DPEHPK3PXP", item?.secret)
        assertEquals(30, item?.periodSeconds)
    }

    @Test
    fun fromUrl_validUrlNoIssuerParam_returnsTotpItem() {
        val url = "otpauth://totp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&period=60"
        val uri = Uri.parse(url)
        val item = TotpItem.fromUrl(uri)

        assertEquals("Example", item?.name)
        assertEquals("user@example.com", item?.extraInfo)
        assertEquals("JBSWY3DPEHPK3PXP", item?.secret)
        assertEquals(60, item?.periodSeconds)
    }

    @Test
    fun fromUrl_validUrlNoPrefix_returnsTotpItem() {
        val url = "otpauth://totp/user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"
        val uri = Uri.parse(url)
        val item = TotpItem.fromUrl(uri)

        assertEquals("Example", item?.name)
        assertEquals("user@example.com", item?.extraInfo)
        assertEquals("JBSWY3DPEHPK3PXP", item?.secret)
        assertEquals(30, item?.periodSeconds)
    }

    @Test
    fun fromUrl_invalidHost_returnsNull() {
        val url = "otpauth://hotp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP"
        val uri = Uri.parse(url)
        val item = TotpItem.fromUrl(uri)

        assertNull(item)
    }

    @Test
    fun fromUrl_missingSecret_returnsNull() {
        val url = "otpauth://totp/Example:user@example.com?issuer=Example"
        val uri = Uri.parse(url)
        val item = TotpItem.fromUrl(uri)

        assertNull(item)
    }

    @Test
    fun fromUrl_multipleColonsInPath_returnsNull() {
        val url = "otpauth://totp/Example:user:extra@example.com?secret=JBSWY3DPEHPK3PXP"
        val uri = Uri.parse(url)
        val item = TotpItem.fromUrl(uri)

        assertNull(item)
    }

    @Test
    fun fromUrl_invalidPeriod_returnsNull() {
        val url = "otpauth://totp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&period=abc"
        val uri = Uri.parse(url)
        val item = TotpItem.fromUrl(uri)

        assertNull(item)
    }

    @Test
    fun fromUrl_noIssuerAndNoPrefix_returnsNull() {
        val url = "otpauth://totp/user@example.com?secret=JBSWY3DPEHPK3PXP"
        val uri = Uri.parse(url)
        val item = TotpItem.fromUrl(uri)

        assertNull(item)
    }
}
