package ltd.evilcorp.nao

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HotpItemTest {
    @Test
    fun fromUrl_validUrl_returnsHotpItem() {
        val url =
            "otpauth://hotp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&counter=42"
        val uri = Uri.parse(url)
        val item = HotpItem.fromUrl(uri)

        assertEquals("Example", item?.name)
        assertEquals("user@example.com", item?.extraInfo)
        assertEquals("JBSWY3DPEHPK3PXP", item?.secret)
        assertEquals(42L, item?.counter)
        assertEquals(Digest.Sha1, item?.digest)
    }

    @Test
    fun fromUrl_validUrlNoCounter_returnsHotpItemWithDefaultCounter() {
        val url = "otpauth://hotp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"
        val uri = Uri.parse(url)
        val item = HotpItem.fromUrl(uri)

        assertEquals(0L, item?.counter)
    }

    @Test
    fun fromUrl_validUrlWithSha256_returnsHotpItem() {
        val url =
            "otpauth://hotp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&counter=1&algorithm=SHA256"
        val uri = Uri.parse(url)
        val item = HotpItem.fromUrl(uri)

        assertEquals(Digest.Sha256, item?.digest)
    }

    @Test
    fun fromUrl_validUrlWithUnknownAlgorithm_returnsNull() {
        val url =
            "otpauth://hotp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&counter=1&algorithm=MD5"
        val uri = Uri.parse(url)
        val item = HotpItem.fromUrl(uri)

        assertEquals(null, item)
    }

    @Test
    fun fromUrl_validUrlNoIssuerParam_returnsHotpItem() {
        val url = "otpauth://hotp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&counter=1"
        val uri = Uri.parse(url)
        val item = HotpItem.fromUrl(uri)

        assertEquals("Example", item?.name)
        assertEquals("user@example.com", item?.extraInfo)
    }

    @Test
    fun fromUrl_validUrlNoPrefix_returnsHotpItem() {
        val url = "otpauth://hotp/user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&counter=1"
        val uri = Uri.parse(url)
        val item = HotpItem.fromUrl(uri)

        assertEquals("Example", item?.name)
        assertEquals("user@example.com", item?.extraInfo)
    }

    @Test
    fun fromUrl_noIssuerAndNoPrefix_returnsNull() {
        val url = "otpauth://hotp/user@example.com?secret=JBSWY3DPEHPK3PXP&counter=1"
        val uri = Uri.parse(url)
        val item = HotpItem.fromUrl(uri)

        assertNull(item)
    }

    @Test
    fun fromUrl_invalidHost_returnsNull() {
        val url = "otpauth://totp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&counter=1"
        val uri = Uri.parse(url)
        val item = HotpItem.fromUrl(uri)

        assertNull(item)
    }

    @Test
    fun fromUrl_missingSecret_returnsNull() {
        val url = "otpauth://hotp/Example:user@example.com?issuer=Example&counter=1"
        val uri = Uri.parse(url)
        val item = HotpItem.fromUrl(uri)

        assertNull(item)
    }

    @Test
    fun fromUrl_invalidCounter_returnsNull() {
        val url = "otpauth://hotp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&counter=abc"
        val uri = Uri.parse(url)
        val item = HotpItem.fromUrl(uri)

        assertNull(item)
    }

    @Test
    fun fromUrl_validUrlWithDigits_returnsHotpItem() {
        val url = "otpauth://hotp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&counter=1&digits=8"
        val uri = Uri.parse(url)
        val item = HotpItem.fromUrl(uri)

        assertEquals(8, item?.otpLength)
    }
}
