package xyz.thewind.panx.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("file_nodes")
data class FileNode(
    @Id
    @Column("id")
    val id: Long? = null,
    @Column("parent_id")
    val parentId: Long? = null,
    @Column("owner_id")
    val ownerId: Long,
    @Column("name")
    val name: String,
    @Column("node_type")
    val nodeType: NodeType,
    @Column("storage_path")
    val storagePath: String? = null,
    @Column("content_type")
    val contentType: String? = null,
    @Column("size")
    val size: Long = 0,
    @Column("extension")
    val extension: String? = null,
    @Column("sha256")
    val sha256: String? = null,
    @Column("deleted")
    val deleted: Boolean = false,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant = Instant.now(),
)
