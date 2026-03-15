package xyz.thewind.panx.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "panx.share")
data class PanxShareProperties(
    val defaultExpireHours: Long = 168,
)
