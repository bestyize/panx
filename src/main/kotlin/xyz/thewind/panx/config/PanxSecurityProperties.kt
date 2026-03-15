package xyz.thewind.panx.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "panx.security")
data class PanxSecurityProperties(
    val initAdmin: InitAdmin = InitAdmin(),
) {
    data class InitAdmin(
        val enabled: Boolean = true,
        val username: String = "admin",
        val password: String = "admin123",
        val displayName: String = "Administrator",
    )
}
