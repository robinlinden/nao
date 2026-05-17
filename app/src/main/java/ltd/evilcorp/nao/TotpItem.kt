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

data class TotpItem(
    val name: String,
    val extraInfo: String,
    val secret: String,
    val periodSeconds: Int,
    val digest: Digest,
    val otpLength: Int = 6,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("name", name)
            put("extraInfo", extraInfo)
            put("secret", secret)
            put("periodSeconds", periodSeconds)
            put("digest", digest.name.lowercase())
            put("otpLength", otpLength)
        }

    companion object {
        fun fromJson(json: JSONObject): TotpItem =
            TotpItem(
                name = json.getString("name"),
                extraInfo = json.getString("extraInfo"),
                secret = json.getString("secret"),
                periodSeconds = json.getInt("periodSeconds"),
                digest = if (json.has("digest")) {
                    when (json.getString("digest").lowercase()) {
                        "sha1" -> Digest.Sha1
                        "sha256" -> Digest.Sha256
                        "sha512" -> Digest.Sha512
                        else -> Digest.Sha1
                    }
                } else {
                    Digest.Sha1
                },
                otpLength = json.optInt("otpLength", 6),
            )

        fun fromUrl(uri: Uri): TotpItem? {
            val pathSegments = uri.pathSegments
            if (pathSegments.size != 1) {
                return null
            }

            val path = pathSegments[0]

            if (uri.host != "totp") {
                return null
            }

            val colons = path.count { it == ':' }
            if (colons != 1 && colons != 0) {
                return null
            }

            val hasIssuerPrefix = colons == 1

            val secret = uri.getQueryParameter("secret") ?: return null

            var name = uri.getQueryParameter("issuer")
            if (name == null && !hasIssuerPrefix) {
                return null
            } else if (name == null) {
                name = path.substringBefore(':')
            }

            val extraInfo = if (hasIssuerPrefix) {
                path.substringAfter(':')
            } else {
                path
            }

            val period = uri.getQueryParameter("period") ?: "30"
            val periodSeconds = period.toIntOrNull() ?: return null

            val algorithm = uri.getQueryParameter("algorithm")?.uppercase() ?: "SHA1"
            val digest = Digest.fromString(algorithm) ?: return null

            val digits = uri.getQueryParameter("digits") ?: "6"
            val otpLength = digits.toIntOrNull() ?: return null

            return TotpItem(
                name = name,
                extraInfo = extraInfo,
                secret = secret,
                periodSeconds = periodSeconds,
                digest = digest,
                otpLength = otpLength,
            )
        }
    }
}
