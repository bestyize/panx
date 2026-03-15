package xyz.thewind.panx.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("download_tasks")
data class DownloadTask(
    @Id
    @Column("id")
    val id: Long? = null,
    @Column("owner_id")
    val ownerId: Long,
    @Column("target_parent_id")
    val targetParentId: Long? = null,
    @Column("file_node_id")
    val fileNodeId: Long? = null,
    @Column("source_url")
    val sourceUrl: String,
    @Column("file_name")
    val fileName: String,
    @Column("status")
    val status: DownloadTaskStatus = DownloadTaskStatus.PENDING,
    @Column("bytes_downloaded")
    val bytesDownloaded: Long = 0,
    @Column("total_bytes")
    val totalBytes: Long? = null,
    @Column("error_message")
    val errorMessage: String? = null,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("updated_at")
    val updatedAt: Instant = Instant.now(),
)
