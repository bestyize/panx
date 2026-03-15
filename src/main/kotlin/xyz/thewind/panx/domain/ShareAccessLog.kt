package xyz.thewind.panx.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("share_access_logs")
data class ShareAccessLog(
    @Id
    @Column("id")
    val id: Long? = null,
    @Column("share_link_id")
    val shareLinkId: Long,
    @Column("access_ip")
    val accessIp: String? = null,
    @Column("user_agent")
    val userAgent: String? = null,
    @Column("action")
    val action: String,
    @Column("accessed_at")
    val accessedAt: Instant = Instant.now(),
)
