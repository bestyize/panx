package xyz.thewind.panx.dto

import xyz.thewind.panx.domain.NodeType
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateShareRequest(
    val fileNodeId: Long,
    val expireHours: Long? = null,
    @field:Size(max = 32)
    val extractCode: String? = null,
)

data class VerifyShareRequest(
    val code: String? = null,
)

data class ShareInfoResponse(
    val id: Long,
    val shareToken: String,
    val fileNodeId: Long,
    val fileName: String,
    val nodeType: NodeType,
    val contentType: String?,
    val size: Long,
    val sizeDisplay: String,
    val expiresAt: Instant?,
    val hasExtractCode: Boolean,
    val enabled: Boolean,
    val previewable: Boolean,
    val iconKey: String,
)

data class SharedFolderBrowseResponse(
    val currentFolderId: Long,
    val currentFolderName: String,
    val breadcrumbs: List<BreadcrumbItem>,
    val items: List<FileNodeResponse>,
)

data class ShareListItemResponse(
    val id: Long,
    val shareToken: String,
    val fileNodeId: Long,
    val fileName: String,
    val nodeType: NodeType,
    val expiresAt: Instant?,
    val hasExtractCode: Boolean,
    val accessCount: Long,
)
