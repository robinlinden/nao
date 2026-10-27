package ltd.evilcorp.nao

import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret

const val DEFAULT_PERIOD = 30
val DEFAULT_DIGEST = Digest.Sha1
const val DEFAULT_OTP_LENGTH = 6

fun validateName(
    name: String,
    emptyErrorMessage: String,
): String? = if (name.isEmpty()) emptyErrorMessage else null

fun isBase32Character(char: Char): Boolean = char in 'A'..'Z' || char in 'a'..'z' || char in '2'..'7' || char == '='

fun validateSecret(
    secret: String,
    digest: Digest,
    base32ErrorMessage: String,
    lengthErrorMessage: String,
): String? {
    if (secret.any { !isBase32Character(it) }) return base32ErrorMessage
    if (secret.substringAfter('=', "").any { it != '=' }) return base32ErrorMessage

    // The RFC for this says that secrets MUST be at least 128 bits. That's sadly not
    // true in practice right now, so we allow secrets that are as weak as 80 bits.
    // https://datatracker.ietf.org/doc/html/rfc6238#section-3
    // https://datatracker.ietf.org/doc/html/rfc4226#section-4
    if (secret.length < 16) return lengthErrorMessage

    return try {
        val s = TOTPSecret.fromBase32EncodedString(secret)
        val generator = TOTPGenerator(digest = asHMACDigest(digest))
        generator.generateCurrent(s)
        null
    } catch (_: Exception) {
        // This should in theory be unreachable due to the prior checks, but 1time's
        // opinions about what's base32 and what isn't are interesting.
        base32ErrorMessage
    }
}

fun validatePeriod(
    period: String,
    integerErrorMessage: String,
    rangeErrorMessage: String,
): String? {
    val p = period.toIntOrNull() ?: return integerErrorMessage
    if (p !in 1..3600) return rangeErrorMessage
    return null
}

fun validateCounter(
    counter: String,
    integerErrorMessage: String,
): String? {
    val c = counter.toLongOrNull() ?: return integerErrorMessage
    if (c < 0) return integerErrorMessage
    return null
}
