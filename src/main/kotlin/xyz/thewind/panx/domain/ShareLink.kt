package xyz.thewind.panx.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("share_links")
data class ShareLink(
    @Id
    @Column("id")
    val id: Long? = null,
    @Column("file_node_id")
    val fileNodeId: Long,
    @Column("share_token")
    val shareToken: String,
    @Column("extract_code")
    val extractCode: String? = null,
    @Column("expires_at")
    val expiresAt: Instant? = null,
    @Column("created_by")
    val createdBy: Long,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("enabled")
    val enabled: Boolean = true,
)
