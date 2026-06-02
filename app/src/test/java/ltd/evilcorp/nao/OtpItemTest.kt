package ltd.evilcorp.nao

import android.net.Uri
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OtpItemTest {
    @Test
    fun fromUrl_totpUrl_returnsTotpInstance() {
        val url = "otpauth://totp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP"
        val uri = Uri.parse(url)
        val item = OtpItem.fromUrl(uri)

        assertTrue(item is OtpItem.Timed)
    }

    @Test
    fun fromUrl_hotpUrl_returnsHotpInstance() {
        val url = "otpauth://hotp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&counter=1"
        val uri = Uri.parse(url)
        val item = OtpItem.fromUrl(uri)

        assertTrue(item is OtpItem.Hashed)
    }
}
