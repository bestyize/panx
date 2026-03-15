package xyz.thewind.panx.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("users")
data class UserAccount(
    @Id
    @Column("id")
    val id: Long? = null,
    @Column("username")
    val username: String,
    @Column("password_hash")
    val passwordHash: String,
    @Column("display_name")
    val displayName: String,
    @Column("enabled")
    val enabled: Boolean = true,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant = Instant.now(),
)
