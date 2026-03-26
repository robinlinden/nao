package ltd.evilcorp.nao

import android.net.Uri

data class TotpItem(
    val name: String,
    val extraInfo: String,
    val secret: String,
    val periodSeconds: Int,
) {
    companion object {
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

            return TotpItem(
                name = name,
                extraInfo = extraInfo,
                secret = secret,
                periodSeconds = periodSeconds,
            )
        }
    }
}
