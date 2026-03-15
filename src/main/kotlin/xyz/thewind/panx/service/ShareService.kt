package xyz.thewind.panx.service

import xyz.thewind.panx.config.PanxShareProperties
import xyz.thewind.panx.domain.FileNode
import xyz.thewind.panx.domain.NodeType
import xyz.thewind.panx.domain.ShareAccessLog
import xyz.thewind.panx.domain.ShareLink
import xyz.thewind.panx.domain.UserAccount
import xyz.thewind.panx.dto.BreadcrumbItem
import xyz.thewind.panx.dto.ShareInfoResponse
import xyz.thewind.panx.dto.ShareListItemResponse
import xyz.thewind.panx.dto.SharedFolderBrowseResponse
import xyz.thewind.panx.exception.BadRequestException
import xyz.thewind.panx.exception.ForbiddenException
import xyz.thewind.panx.exception.NotFoundException
import xyz.thewind.panx.repository.ShareAccessLogRepository
import xyz.thewind.panx.repository.ShareLinkRepository
import xyz.thewind.panx.support.FileSizeFormatter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

@Service
class ShareService(
    private val shareLinkRepository: ShareLinkRepository,
    private val shareAccessLogRepository: ShareAccessLogRepository,
    private val fileService: FileService,
    private val shareProperties: PanxShareProperties,
) {

    private val secureRandom = SecureRandom()

    @Transactional
    fun createShare(owner: UserAccount, fileNodeId: Long, expireHours: Long?, extractCode: String?): ShareInfoResponse {
        val fileNode = fileService.getShareableNode(owner, fileNodeId)
        val now = Instant.now()
        val expiresAt = expireHours?.takeIf { it > 0 }?.let { now.plus(it, ChronoUnit.HOURS) }
            ?: now.plus(shareProperties.defaultExpireHours, ChronoUnit.HOURS)
        val share = shareLinkRepository.save(
            ShareLink(
                fileNodeId = fileNode.id!!,
                shareToken = newToken(),
                extractCode = extractCode?.trim()?.takeIf { it.isNotBlank() },
                expiresAt = expiresAt,
                createdBy = owner.id!!,
                createdAt = now,
                enabled = true,
            ),
        )
        return toResponse(share, fileNode)
    }

    fun getShareInfo(token: String): ShareInfoResponse {
        val share = getActiveShare(token)
        val fileNode = fileService.getNode(share.createdBy, share.fileNodeId)
        return toResponse(share, fileNode)
    }

    fun listShares(owner: UserAccount): List<ShareListItemResponse> = shareLinkRepository.findAll()
        .asSequence()
        .filter { it.createdBy == owner.id && it.enabled }
        .sortedByDescending { it.createdAt }
        .mapNotNull { share ->
            runCatching {
                val node = fileService.getNode(share.createdBy, share.fileNodeId)
                ShareListItemResponse(
                    id = share.id!!,
                    shareToken = share.shareToken,
                    fileNodeId = share.fileNodeId,
                    fileName = node.name,
                    nodeType = node.nodeType,
                    expiresAt = share.expiresAt,
                    hasExtractCode = !share.extractCode.isNullOrBlank(),
                    accessCount = shareAccessLogRepository.countByShareLinkId(share.id!!),
                )
            }.getOrNull()
        }
        .toList()

    fun isVerified(token: String, session: HttpSession?): Boolean {
        val share = getActiveShare(token)
        if (share.extractCode.isNullOrBlank()) {
            return true
        }
        return session?.getAttribute(sessionKey(share.shareToken)) as? Boolean ?: false
    }

    fun getFolderView(token: String, parentId: Long?, session: HttpSession?): SharedFolderBrowseResponse {
        val share = getActiveShare(token)
        val rootNode = fileService.getNode(share.createdBy, share.fileNodeId)
        if (rootNode.nodeType != NodeType.FOLDER) {
            throw BadRequestException("Shared node is not a folder")
        }
        ensureVerifiedIfNeeded(share, null, session)

        val currentFolder = when (parentId) {
            null -> rootNode
            else -> fileService.getNode(share.createdBy, parentId).also { folder ->
                if (folder.nodeType != NodeType.FOLDER || !fileService.isNodeInsideFolder(share.createdBy, folder.id!!, rootNode.id!!)) {
                    throw NotFoundException("Shared folder not found")
                }
            }
        }

        val items = fileService.listChildNodes(share.createdBy, currentFolder.id).map(fileService::toResponse)
        return SharedFolderBrowseResponse(
            currentFolderId = currentFolder.id!!,
            currentFolderName = currentFolder.name,
            breadcrumbs = buildFolderBreadcrumbs(share.createdBy, rootNode, currentFolder),
            items = items,
        )
    }

    fun verify(token: String, code: String?, session: HttpSession): Boolean {
        val share = getActiveShare(token)
        if (share.extractCode.isNullOrBlank()) {
            return true
        }
        if (code.isNullOrBlank() || share.extractCode != code.trim()) {
            throw ForbiddenException("Invalid extract code")
        }
        session.setAttribute(sessionKey(token), true)
        return true
    }

    fun resolveDownload(token: String, nodeId: Long?, code: String?, session: HttpSession?): FileNode {
        val share = getActiveShare(token)
        ensureVerifiedIfNeeded(share, code, session)

        val sharedNode = fileService.getNode(share.createdBy, share.fileNodeId)
        return when (sharedNode.nodeType) {
            NodeType.FILE -> sharedNode
            NodeType.FOLDER -> {
                val requestedNodeId = nodeId ?: throw BadRequestException("Folder share download requires nodeId")
                fileService.getNode(share.createdBy, requestedNodeId).also { node ->
                    if (node.nodeType != NodeType.FILE || !fileService.isNodeInsideFolder(share.createdBy, node.id!!, sharedNode.id!!)) {
                        throw BadRequestException("Requested node is not downloadable in this share")
                    }
                }
            }
        }
    }

    fun resolvePreview(token: String, nodeId: Long?, code: String?, session: HttpSession?): FileNode =
        resolveDownload(token, nodeId, code, session)

    @Transactional
    fun cancelShare(owner: UserAccount, id: Long) {
        val share = shareLinkRepository.findById(id).orElseThrow { NotFoundException("Share not found") }
        if (share.createdBy != owner.id) {
            throw ForbiddenException("Cannot cancel another user's share")
        }
        shareLinkRepository.save(share.copy(enabled = false))
    }

    fun recordAccess(token: String, request: HttpServletRequest, action: String) {
        val share = getActiveShare(token)
        shareAccessLogRepository.save(
            ShareAccessLog(
                shareLinkId = share.id!!,
                accessIp = request.remoteAddr,
                userAgent = request.getHeader("User-Agent"),
                action = action,
                accessedAt = Instant.now(),
            ),
        )
    }

    private fun getActiveShare(token: String): ShareLink {
        val share = shareLinkRepository.findByShareToken(token) ?: throw NotFoundException("Share not found")
        if (!share.enabled) {
            throw NotFoundException("Share not found")
        }
        if (share.expiresAt != null && share.expiresAt.isBefore(Instant.now())) {
            throw NotFoundException("Share has expired")
        }
        return share
    }

    private fun toResponse(share: ShareLink, fileNode: FileNode): ShareInfoResponse {
        val fileResponse = fileService.toResponse(fileNode)
        return ShareInfoResponse(
            id = share.id!!,
            shareToken = share.shareToken,
            fileNodeId = share.fileNodeId,
            fileName = fileNode.name,
            nodeType = fileNode.nodeType,
            contentType = fileNode.contentType,
            size = fileNode.size,
            sizeDisplay = FileSizeFormatter.format(fileNode.size),
            expiresAt = share.expiresAt,
            hasExtractCode = !share.extractCode.isNullOrBlank(),
            enabled = share.enabled,
            previewable = fileResponse.previewable,
            iconKey = fileResponse.iconKey,
        )
    }

    private fun newToken(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun ensureVerifiedIfNeeded(share: ShareLink, code: String?, session: HttpSession?) {
        if (!share.extractCode.isNullOrBlank()) {
            val verified = session?.getAttribute(sessionKey(share.shareToken)) as? Boolean ?: false
            if (!verified && share.extractCode != code?.trim()) {
                throw ForbiddenException("Extract code verification required")
            }
            if (!verified && share.extractCode == code?.trim()) {
                session?.setAttribute(sessionKey(share.shareToken), true)
            }
        }
    }

    private fun buildFolderBreadcrumbs(ownerId: Long, rootNode: FileNode, currentFolder: FileNode): List<BreadcrumbItem> {
        val stack = ArrayDeque<FileNode>()
        var cursor: FileNode? = currentFolder
        while (cursor != null) {
            stack.addFirst(cursor)
            if (cursor.id == rootNode.id) {
                break
            }
            cursor = cursor.parentId?.let { fileService.getNode(ownerId, it) }
        }
        return stack.map { BreadcrumbItem(id = it.id, name = it.name) }
    }

    private fun sessionKey(token: String): String = "panx:share:$token:verified"
}
