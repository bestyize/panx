package xyz.thewind.panx.dto

import xyz.thewind.panx.domain.NodeType
import xyz.thewind.panx.domain.DownloadTaskStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateFolderRequest(
    val parentId: Long? = null,
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
)

data class RenameNodeRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
)

data class MoveNodeRequest(
    val targetParentId: Long? = null,
)

data class CreateTextFileRequest(
    val parentId: Long? = null,
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,
    val content: String = "",
)

data class CreateRemoteDownloadTaskRequest(
    val parentId: Long? = null,
    @field:NotBlank
    @field:Size(max = 2048)
    val sourceUrl: String,
    @field:Size(max = 255)
    val fileName: String? = null,
)

data class FileNodeResponse(
    val id: Long,
    val parentId: Long?,
    val name: String,
    val nodeType: NodeType,
    val contentType: String?,
    val size: Long,
    val sizeDisplay: String,
    val extension: String?,
    val updatedAt: Instant,
    val previewable: Boolean,
    val iconKey: String,
)

data class BreadcrumbItem(
    val id: Long?,
    val name: String,
)

data class FileListResponse(
    val parentId: Long?,
    val currentFolderName: String,
    val breadcrumbs: List<BreadcrumbItem>,
    val items: List<FileNodeResponse>,
)

data class FolderOptionResponse(
    val id: Long?,
    val parentId: Long?,
    val name: String,
    val path: String,
)

data class DownloadTaskResponse(
    val id: Long,
    val targetParentId: Long?,
    val fileNodeId: Long?,
    val sourceUrl: String,
    val fileName: String,
    val status: DownloadTaskStatus,
    val bytesDownloaded: Long,
    val bytesDownloadedDisplay: String,
    val totalBytes: Long?,
    val totalBytesDisplay: String?,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
