package ltd.evilcorp.nao

import com.atlassian.onetime.core.HMACDigest
import com.atlassian.onetime.core.OTPLength

fun asHMACDigest(digest: Digest) =
    when (digest) {
        Digest.Sha1 -> HMACDigest.SHA1
        Digest.Sha256 -> HMACDigest.SHA256
        Digest.Sha512 -> HMACDigest.SHA512
    }

fun asOTPLength(length: Int) =
    when (length) {
        6 -> OTPLength.SIX
        7 -> OTPLength.SEVEN
        8 -> OTPLength.EIGHT
        else -> OTPLength.SIX
    }
