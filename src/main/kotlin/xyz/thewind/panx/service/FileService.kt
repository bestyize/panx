package xyz.thewind.panx.service

import xyz.thewind.panx.config.PanxStorageProperties
import xyz.thewind.panx.domain.FileNode
import xyz.thewind.panx.domain.NodeType
import xyz.thewind.panx.domain.UserAccount
import xyz.thewind.panx.dto.BreadcrumbItem
import xyz.thewind.panx.dto.FileListResponse
import xyz.thewind.panx.dto.FileNodeResponse
import xyz.thewind.panx.dto.FolderOptionResponse
import xyz.thewind.panx.exception.BadRequestException
import xyz.thewind.panx.exception.ConflictException
import xyz.thewind.panx.exception.NotFoundException
import xyz.thewind.panx.repository.FileNodeRepository
import xyz.thewind.panx.repository.ShareAccessLogRepository
import xyz.thewind.panx.repository.ShareLinkRepository
import xyz.thewind.panx.support.FilenameSanitizer
import xyz.thewind.panx.support.FileSizeFormatter
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.Instant

@Service
class FileService(
    private val fileNodeRepository: FileNodeRepository,
    private val shareLinkRepository: ShareLinkRepository,
    private val shareAccessLogRepository: ShareAccessLogRepository,
    private val storageService: StorageService,
    private val storageProperties: PanxStorageProperties,
) {

    fun listNodes(owner: UserAccount, parentId: Long?): FileListResponse {
        val currentFolder = parentId?.let { getFolder(owner.id!!, it) }
        val items = if (parentId == null) {
            fileNodeRepository.findRootNodes(owner.id!!)
        } else {
            fileNodeRepository.findByOwnerIdAndParentId(owner.id!!, parentId)
        }
        return FileListResponse(
            parentId = parentId,
            currentFolderName = currentFolder?.name ?: "Root",
            breadcrumbs = buildBreadcrumbs(owner.id!!, currentFolder),
            items = items.map(::toResponse),
        )
    }

    fun search(owner: UserAccount, keyword: String): List<FileNodeResponse> {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isBlank()) {
            return emptyList()
        }
        return fileNodeRepository.searchByOwnerIdAndKeyword(owner.id!!, normalizedKeyword).map(::toResponse)
    }

    fun listFolders(owner: UserAccount, excludeNodeId: Long? = null): List<FolderOptionResponse> {
        val folders = fileNodeRepository.findAllFoldersByOwnerId(owner.id!!)
        val folderMap = folders.associateBy { it.id!! }

        return buildList {
            add(
                FolderOptionResponse(
                    id = null,
                    parentId = null,
                    name = "Root",
                    path = "/",
                ),
            )
            folders
                .filterNot { folder ->
                    val folderId = folder.id!!
                    folderId == excludeNodeId || (excludeNodeId != null && isDescendant(owner.id!!, folder, excludeNodeId))
                }
                .sortedBy { buildFolderPath(it, folderMap) }
                .forEach { folder ->
                    add(
                        FolderOptionResponse(
                            id = folder.id,
                            parentId = folder.parentId,
                            name = folder.name,
                            path = buildFolderPath(folder, folderMap),
                        ),
                    )
                }
        }
    }

    @Transactional
    fun createFolder(owner: UserAccount, parentId: Long?, name: String): FileNodeResponse {
        val sanitizedName = sanitizeName(name)
        parentId?.let { getFolder(owner.id!!, it) }
        ensureSiblingNameAvailable(owner.id!!, parentId, sanitizedName)

        val now = Instant.now()
        val node = fileNodeRepository.save(
            FileNode(
                parentId = parentId,
                ownerId = owner.id!!,
                name = sanitizedName,
                nodeType = NodeType.FOLDER,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return toResponse(node)
    }

    @Transactional
    fun upload(owner: UserAccount, parentId: Long?, files: List<MultipartFile>, relativePaths: List<String>? = null): List<FileNodeResponse> {
        if (files.isEmpty()) {
            throw BadRequestException("No files uploaded")
        }
        parentId?.let { getFolder(owner.id!!, it) }
        val folderCache = mutableMapOf<Pair<Long?, String>, Long?>()

        return files.mapIndexedNotNull { index, file ->
            if (file.isEmpty) {
                return@mapIndexedNotNull null
            }
            val relativePath = relativePaths?.getOrNull(index)
            val (targetParentId, sanitizedName) = resolveUploadTarget(owner.id!!, parentId, relativePath, file.originalFilename, folderCache)
            val storedNode = storeFileNode(
                ownerId = owner.id!!,
                parentId = targetParentId,
                fileName = sanitizedName,
                contentType = file.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE,
                inputStream = file.inputStream,
                declaredSize = file.size,
            )
            toResponse(storedNode)
        }
    }

    @Transactional
    fun createTextFile(owner: UserAccount, parentId: Long?, name: String, content: String): FileNodeResponse {
        parentId?.let { getFolder(owner.id!!, it) }
        val storedNode = storeFileNode(
            ownerId = owner.id!!,
            parentId = parentId,
            fileName = sanitizeName(name),
            contentType = MediaType.TEXT_PLAIN_VALUE,
            inputStream = ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8)),
            declaredSize = content.toByteArray(StandardCharsets.UTF_8).size.toLong(),
        )
        return toResponse(storedNode)
    }

    fun getFileForDownload(owner: UserAccount, id: Long): FileNode = getFile(owner.id!!, id)

    fun loadResource(node: FileNode): Resource {
        val storagePath = node.storagePath ?: throw NotFoundException("Stored file not found")
        return storageService.loadAsResource(storagePath)
    }

    fun getFileForPreview(owner: UserAccount, id: Long): FilePreview {
        val node = getFile(owner.id!!, id)
        return getFileForPreviewByNode(node)
    }

    fun getFileForPreview(ownerId: Long, id: Long): FilePreview {
        val node = getFile(ownerId, id)
        return getFileForPreviewByNode(node)
    }

    fun getFileForPreviewByNode(node: FileNode): FilePreview {
        val storagePath = node.storagePath ?: throw NotFoundException("Stored file not found")
        val contentType = node.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE
        return when {
            isBinaryPreviewable(contentType, node.extension) -> FilePreview.Binary(node, storageService.loadAsResource(storagePath))
            isTextPreviewable(contentType, node.extension) -> FilePreview.Text(
                node = node,
                content = storageService.readTextPreview(storagePath, storageProperties.maxTextPreviewBytes),
            )
            else -> throw BadRequestException("Preview is only available for image, text, video and audio files")
        }
    }

    @Transactional
    fun rename(owner: UserAccount, id: Long, name: String): FileNodeResponse {
        val node = getNode(owner.id!!, id)
        val sanitizedName = sanitizeName(name)
        if (node.name == sanitizedName) {
            return toResponse(node)
        }
        ensureSiblingNameAvailable(owner.id!!, node.parentId, sanitizedName, node.id!!)
        val updated = fileNodeRepository.save(node.copy(name = sanitizedName, extension = extensionOf(sanitizedName), updatedAt = Instant.now()))
        return toResponse(updated)
    }

    @Transactional
    fun move(owner: UserAccount, id: Long, targetParentId: Long?): FileNodeResponse {
        val node = getNode(owner.id!!, id)
        if (node.parentId == targetParentId) {
            return toResponse(node)
        }
        if (targetParentId == node.id) {
            throw BadRequestException("Cannot move a node into itself")
        }
        val targetParent = targetParentId?.let { getFolder(owner.id!!, it) }
        if (node.nodeType == NodeType.FOLDER && targetParent != null && isDescendant(owner.id!!, targetParent, node.id!!)) {
            throw BadRequestException("Cannot move a folder into its descendant")
        }
        ensureSiblingNameAvailable(owner.id!!, targetParentId, node.name, node.id!!)
        val updated = fileNodeRepository.save(node.copy(parentId = targetParentId, updatedAt = Instant.now()))
        return toResponse(updated)
    }

    @Transactional
    fun delete(owner: UserAccount, id: Long, recursive: Boolean) {
        val node = getNode(owner.id!!, id)
        deleteNode(node, recursive)
    }

    @Transactional
    fun deleteNodeByOwnerId(ownerId: Long, id: Long, recursive: Boolean) {
        val node = getNode(ownerId, id)
        deleteNode(node, recursive)
    }

    fun getNode(ownerId: Long, id: Long): FileNode = fileNodeRepository.findActiveByIdAndOwnerId(id, ownerId)
        ?: throw NotFoundException("File node not found")

    fun getShareableNode(owner: UserAccount, id: Long): FileNode = getNode(owner.id!!, id)

    fun requireFolder(ownerId: Long, id: Long): FileNode = getFolder(ownerId, id)

    fun isVideoNode(node: FileNode): Boolean = iconKey(node) == "video"

    fun isAudioNode(node: FileNode): Boolean = iconKey(node) == "audio"

    fun isPlayerMedia(node: FileNode): Boolean = isVideoNode(node) || isAudioNode(node)

    @Transactional
    fun storeStreamedFile(
        owner: UserAccount,
        parentId: Long?,
        fileName: String,
        contentType: String,
        inputStream: InputStream,
        shouldContinue: () -> Boolean = { true },
        onProgress: (Long) -> Unit = {},
    ): FileNode {
        parentId?.let { getFolder(owner.id!!, it) }
        return storeFileNode(
            ownerId = owner.id!!,
            parentId = parentId,
            fileName = sanitizeName(fileName),
            contentType = contentType,
            inputStream = inputStream,
            declaredSize = 0,
            shouldContinue = shouldContinue,
            onProgress = onProgress,
        )
    }

    fun listChildNodes(ownerId: Long, parentId: Long?): List<FileNode> = if (parentId == null) {
        fileNodeRepository.findRootNodes(ownerId)
    } else {
        fileNodeRepository.findByOwnerIdAndParentId(ownerId, parentId)
    }

    fun isNodeInsideFolder(ownerId: Long, nodeId: Long, folderId: Long): Boolean {
        var cursor: FileNode? = getNode(ownerId, nodeId)
        while (cursor != null) {
            if (cursor.id == folderId) {
                return true
            }
            cursor = cursor.parentId?.let { getNode(ownerId, it) }
        }
        return false
    }

    private fun getFile(ownerId: Long, id: Long): FileNode {
        val node = getNode(ownerId, id)
        if (node.nodeType != NodeType.FILE) {
            throw BadRequestException("Folder does not support this operation")
        }
        return node
    }

    private fun getFolder(ownerId: Long, id: Long): FileNode {
        val node = getNode(ownerId, id)
        if (node.nodeType != NodeType.FOLDER) {
            throw BadRequestException("Target is not a folder")
        }
        return node
    }

    private fun resolveUploadTarget(
        ownerId: Long,
        parentId: Long?,
        relativePath: String?,
        originalFilename: String?,
        folderCache: MutableMap<Pair<Long?, String>, Long?>,
    ): Pair<Long?, String> {
        val normalizedPath = relativePath
            ?.replace('\\', '/')
            ?.trim()
            ?.trim('/')
            ?.takeIf { it.isNotBlank() }
        val segments = (normalizedPath ?: originalFilename ?: "file")
            .split('/')
            .filter { it.isNotBlank() }
        val sanitizedName = sanitizeName(segments.lastOrNull() ?: originalFilename ?: "file")
        if (segments.size == 1) {
            return parentId to sanitizedName
        }

        var currentParentId = parentId
        segments.dropLast(1).forEach { segment ->
            val folderName = sanitizeName(segment)
            val key = currentParentId to folderName
            currentParentId = folderCache.getOrPut(key) {
                fileNodeRepository.findActiveByOwnerIdAndParentIdAndNameAndNodeType(ownerId, currentParentId, folderName, NodeType.FOLDER.name)
                    ?.id
                    ?: createFolderInternal(ownerId, currentParentId, folderName).id
            }
        }
        return currentParentId to sanitizedName
    }

    private fun createFolderInternal(ownerId: Long, parentId: Long?, name: String): FileNode {
        val now = Instant.now()
        return fileNodeRepository.save(
            FileNode(
                parentId = parentId,
                ownerId = ownerId,
                name = name,
                nodeType = NodeType.FOLDER,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun storeFileNode(
        ownerId: Long,
        parentId: Long?,
        fileName: String,
        contentType: String,
        inputStream: InputStream,
        declaredSize: Long,
        shouldContinue: () -> Boolean = { true },
        onProgress: (Long) -> Unit = {},
    ): FileNode {
        ensureSiblingNameAvailable(ownerId, parentId, fileName)
        val now = Instant.now()
        val initialNode = fileNodeRepository.save(
            FileNode(
                parentId = parentId,
                ownerId = ownerId,
                name = fileName,
                nodeType = NodeType.FILE,
                contentType = contentType,
                size = declaredSize,
                extension = extensionOf(fileName),
                createdAt = now,
                updatedAt = now,
            ),
        )
        return try {
            val storedFile = inputStream.use { stream ->
                storageService.storeStreaming(ownerId, initialNode.id!!, fileName, stream, shouldContinue, onProgress)
            }
            fileNodeRepository.save(
                initialNode.copy(
                    storagePath = storedFile.relativePath,
                    size = storedFile.size,
                    updatedAt = Instant.now(),
                ),
            )
        } catch (ex: Exception) {
            fileNodeRepository.deleteById(initialNode.id!!)
            throw ex
        }
    }

    private fun deleteNode(node: FileNode, recursive: Boolean) {
        if (node.nodeType == NodeType.FOLDER) {
            val children = fileNodeRepository.findActiveChildren(node.id!!)
            if (children.isNotEmpty() && !recursive) {
                throw BadRequestException("Folder is not empty; use recursive delete")
            }
            children.forEach { deleteNode(it, true) }
        } else {
            cleanupShares(node.id!!)
            storageService.delete(node.storagePath)
            fileNodeRepository.deleteById(node.id!!)
            return
        }
        cleanupShares(node.id!!)
        fileNodeRepository.deleteById(node.id!!)
    }

    private fun cleanupShares(nodeId: Long) {
        shareLinkRepository.findByFileNodeId(nodeId).forEach { share ->
            share.id?.let { shareId ->
                shareAccessLogRepository.deleteByShareLinkId(shareId)
                shareLinkRepository.deleteById(shareId)
            }
        }
    }

    private fun ensureSiblingNameAvailable(ownerId: Long, parentId: Long?, name: String, excludeId: Long? = null) {
        val exists = if (excludeId == null) {
            fileNodeRepository.existsActiveSibling(ownerId, parentId, name)
        } else {
            fileNodeRepository.existsActiveSiblingExcludingId(ownerId, parentId, name, excludeId)
        }
        if (exists) {
            throw ConflictException("A node with the same name already exists in this folder")
        }
    }

    private fun buildBreadcrumbs(ownerId: Long, currentFolder: FileNode?): List<BreadcrumbItem> {
        val items = mutableListOf(BreadcrumbItem(id = null, name = "Root"))
        var cursor = currentFolder
        val stack = ArrayDeque<FileNode>()
        while (cursor != null) {
            stack.addFirst(cursor)
            cursor = cursor.parentId?.let { getNode(ownerId, it) }
        }
        stack.forEach { items.add(BreadcrumbItem(id = it.id, name = it.name)) }
        return items
    }

    private fun buildFolderPath(folder: FileNode, folderMap: Map<Long, FileNode>): String {
        val segments = ArrayDeque<String>()
        var cursor: FileNode? = folder
        while (cursor != null) {
            segments.addFirst(cursor.name)
            cursor = cursor.parentId?.let { folderMap[it] }
        }
        return "/" + segments.joinToString("/")
    }

    private fun sanitizeName(name: String): String {
        val sanitized = FilenameSanitizer.sanitize(name)
        if (sanitized.isBlank() || sanitized == "." || sanitized == "..") {
            throw BadRequestException("Invalid file name")
        }
        return sanitized
    }

    private fun extensionOf(name: String): String? = name.substringAfterLast('.', "").takeIf { it.isNotBlank() }

    private fun isDescendant(ownerId: Long, targetParent: FileNode, ancestorId: Long): Boolean {
        var cursor: FileNode? = targetParent
        while (cursor != null) {
            if (cursor.id == ancestorId) {
                return true
            }
            cursor = cursor.parentId?.let { getNode(ownerId, it) }
        }
        return false
    }

    private fun isTextPreviewable(contentType: String, extension: String?): Boolean {
        if (contentType.startsWith("text/")) {
            return true
        }
        return contentType in setOf("application/json", "application/xml", "application/javascript") ||
            extension in setOf("md", "txt", "log", "csv", "json", "xml", "yaml", "yml")
    }

    private fun isBinaryPreviewable(contentType: String, extension: String?): Boolean {
        if (contentType.startsWith("image/") || contentType.startsWith("video/") || contentType.startsWith("audio/")) {
            return true
        }
        return extension in setOf("png", "jpg", "jpeg", "gif", "webp", "mp4", "webm", "mkv", "avi", "mov", "mp3", "wav", "ogg", "m4a")
    }

    private fun isPreviewable(node: FileNode): Boolean {
        val contentType = node.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE
        return isBinaryPreviewable(contentType, node.extension) || isTextPreviewable(contentType, node.extension)
    }

    private fun iconKey(node: FileNode): String {
        if (node.nodeType == NodeType.FOLDER) {
            return "folder"
        }
        val contentType = node.contentType ?: ""
        return when {
            contentType.startsWith("image/") || node.extension in setOf("png", "jpg", "jpeg", "gif", "webp") -> "image"
            contentType.startsWith("video/") || node.extension in setOf("mp4", "webm", "mkv", "avi", "mov") -> "video"
            contentType.startsWith("audio/") || node.extension in setOf("mp3", "wav", "ogg", "m4a") -> "audio"
            isTextPreviewable(contentType, node.extension) -> "text"
            node.extension == "pdf" -> "pdf"
            else -> "file"
        }
    }

    fun toResponse(node: FileNode): FileNodeResponse = FileNodeResponse(
        id = node.id!!,
        parentId = node.parentId,
        name = node.name,
        nodeType = node.nodeType,
        contentType = node.contentType,
        size = node.size,
        sizeDisplay = FileSizeFormatter.format(node.size),
        extension = node.extension,
        updatedAt = node.updatedAt,
        previewable = node.nodeType == NodeType.FILE && isPreviewable(node),
        iconKey = iconKey(node),
    )
}

sealed class FilePreview(open val node: FileNode) {
    data class Binary(
        override val node: FileNode,
        val resource: org.springframework.core.io.Resource,
    ) : FilePreview(node)

    data class Text(
        override val node: FileNode,
        val content: String,
    ) : FilePreview(node)
}
