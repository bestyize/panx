package xyz.thewind.panx.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path

@ConfigurationProperties(prefix = "panx.storage")
data class PanxStorageProperties(
    val rootDir: Path,
    val maxTextPreviewBytes: Int = 65_536,
)
