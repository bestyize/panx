package xyz.thewind.panx.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "panx.security")
data class PanxSecurityProperties(
    val initAdmin: InitAdmin = InitAdmin(),
    val mediaAccess: MediaAccess = MediaAccess(),
) {
    data class InitAdmin(
        val enabled: Boolean = true,
        val username: String = "admin",
        val password: String = "admin123",
        val displayName: String = "Administrator",
    )

    data class MediaAccess(
        val tokenSecret: String = "panx-local-media-access-secret-change-me",
        val tokenTtlSeconds: Long = 1800,
    )
}
