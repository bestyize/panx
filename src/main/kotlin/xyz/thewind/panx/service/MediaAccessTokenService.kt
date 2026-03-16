package xyz.thewind.panx.service

import xyz.thewind.panx.config.PanxSecurityProperties
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class MediaAccessTokenService(
    private val securityProperties: PanxSecurityProperties,
) {

    fun issueFilePreviewToken(ownerId: Long, fileId: Long): String {
        val expiresAt = Instant.now().epochSecond + securityProperties.mediaAccess.tokenTtlSeconds
        val payload = "$ownerId:$fileId:$expiresAt"
        val encodedPayload = base64UrlEncoder.encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        val signature = sign(payload)
        return "$encodedPayload.$signature"
    }

    fun validateFilePreviewToken(token: String?, fileId: Long): Long? {
        if (token.isNullOrBlank()) {
            return null
        }
        val parts = token.split('.', limit = 2)
        if (parts.size != 2) {
            return null
        }
        val payload = runCatching {
            String(base64UrlDecoder.decode(parts[0]), StandardCharsets.UTF_8)
        }.getOrNull() ?: return null
        val expectedSignature = sign(payload)
        if (expectedSignature != parts[1]) {
            return null
        }
        val values = payload.split(':', limit = 3)
        if (values.size != 3) {
            return null
        }
        val ownerId = values[0].toLongOrNull() ?: return null
        val tokenFileId = values[1].toLongOrNull() ?: return null
        val expiresAt = values[2].toLongOrNull() ?: return null
        if (tokenFileId != fileId || expiresAt < Instant.now().epochSecond) {
            return null
        }
        return ownerId
    }

    private fun sign(payload: String): String {
        val secret = securityProperties.mediaAccess.tokenSecret.toByteArray(StandardCharsets.UTF_8)
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret, HMAC_ALGORITHM))
        return base64UrlEncoder.encodeToString(mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)))
    }

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private val base64UrlEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        private val base64UrlDecoder: Base64.Decoder = Base64.getUrlDecoder()
    }
}
