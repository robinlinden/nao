package ltd.evilcorp.nao

import android.net.Uri
import org.json.JSONObject

enum class Digest {
    Sha1,
    Sha256,
    Sha512,
    ;

    companion object {
        fun fromString(value: String): Digest? =
            when (value.lowercase()) {
                "sha1" -> Sha1
                "sha256" -> Sha256
                "sha512" -> Sha512
                else -> null
            }
    }
}

sealed class OtpItem {
    abstract val name: String
    abstract val extraInfo: String
    abstract val secret: String
    abstract val digest: Digest
    abstract val otpLength: Int

    abstract fun toJson(): JSONObject

    data class Timed(
        override val name: String,
        override val extraInfo: String,
        override val secret: String,
        val periodSeconds: Int,
        override val digest: Digest,
        override val otpLength: Int = 6,
    ) : OtpItem() {
        override fun toJson(): JSONObject =
            JSONObject().apply {
                put("name", name)
                put("extraInfo", extraInfo)
                put("secret", secret)
                put("periodSeconds", periodSeconds)
                put("digest", digest.name.lowercase())
                put("otpLength", otpLength)
            }

        companion object {
            fun fromJson(json: JSONObject): Timed =
                Timed(
                    name = json.getString("name"),
                    extraInfo = json.getString("extraInfo"),
                    secret = json.getString("secret"),
                    periodSeconds = json.getInt("periodSeconds"),
                    digest = json.optString("digest", "sha1").let { Digest.fromString(it) } ?: Digest.Sha1,
                    otpLength = json.optInt("otpLength", 6),
                )

            fun fromUrl(uri: Uri): Timed? = OtpItem.fromUrl(uri) as? Timed
        }
    }

    data class Hashed(
        override val name: String,
        override val extraInfo: String,
        override val secret: String,
        val counter: Long,
        override val digest: Digest,
        override val otpLength: Int = 6,
    ) : OtpItem() {
        override fun toJson(): JSONObject =
            JSONObject().apply {
                put("name", name)
                put("extraInfo", extraInfo)
                put("secret", secret)
                put("counter", counter)
                put("digest", digest.name.lowercase())
                put("otpLength", otpLength)
            }

        companion object {
            fun fromJson(json: JSONObject): Hashed =
                Hashed(
                    name = json.getString("name"),
                    extraInfo = json.getString("extraInfo"),
                    secret = json.getString("secret"),
                    counter = json.getLong("counter"),
                    digest = json.optString("digest", "sha1").let { Digest.fromString(it) } ?: Digest.Sha1,
                    otpLength = json.optInt("otpLength", 6),
                )

            fun fromUrl(uri: Uri): Hashed? = OtpItem.fromUrl(uri) as? Hashed
        }
    }

    companion object {
        fun fromJson(json: JSONObject): OtpItem =
            when {
                json.has("periodSeconds") -> Timed.fromJson(json)
                json.has("counter") -> Hashed.fromJson(json)
                else -> throw IllegalArgumentException("JSONObject does not contain a valid OTP item (missing periodSeconds or counter)")
            }

        fun fromUrl(uri: Uri): OtpItem? {
            val pathSegments = uri.pathSegments
            if (pathSegments.size != 1) return null

            val path = pathSegments[0]
            val colons = path.count { it == ':' }
            if (colons > 1) return null

            val hasIssuerPrefix = colons == 1
            val secret = uri.getQueryParameter("secret") ?: return null

            var name = uri.getQueryParameter("issuer")
            if (name == null && !hasIssuerPrefix) return null
            if (name == null) name = path.substringBefore(':')

            val extraInfo = if (hasIssuerPrefix) path.substringAfter(':') else path

            val algorithm = uri.getQueryParameter("algorithm")?.uppercase() ?: "SHA1"
            val digest = Digest.fromString(algorithm) ?: return null

            val digits = uri.getQueryParameter("digits") ?: "6"
            val otpLength = digits.toIntOrNull() ?: return null

            return when (uri.host) {
                "totp" -> {
                    val period = uri.getQueryParameter("period") ?: "30"
                    val periodSeconds = period.toIntOrNull() ?: return null
                    Timed(name, extraInfo, secret, periodSeconds, digest, otpLength)
                }

                "hotp" -> {
                    val counterString = uri.getQueryParameter("counter") ?: "0"
                    val counter = counterString.toLongOrNull() ?: return null
                    Hashed(name, extraInfo, secret, counter, digest, otpLength)
                }

                else -> {
                    null
                }
            }
        }
    }
}

typealias TotpItem = OtpItem.Timed
typealias HotpItem = OtpItem.Hashed
