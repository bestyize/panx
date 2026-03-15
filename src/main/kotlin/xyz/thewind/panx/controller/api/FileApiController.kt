package xyz.thewind.panx.controller.api

import xyz.thewind.panx.dto.ApiResponse
import xyz.thewind.panx.dto.CreateRemoteDownloadTaskRequest
import xyz.thewind.panx.dto.CreateFolderRequest
import xyz.thewind.panx.dto.CreateTextFileRequest
import xyz.thewind.panx.dto.DownloadTaskResponse
import xyz.thewind.panx.dto.FileListResponse
import xyz.thewind.panx.dto.FileNodeResponse
import xyz.thewind.panx.dto.FolderOptionResponse
import xyz.thewind.panx.dto.MoveNodeRequest
import xyz.thewind.panx.dto.RenameNodeRequest
import xyz.thewind.panx.service.CurrentUserService
import xyz.thewind.panx.service.DownloadTaskService
import xyz.thewind.panx.service.FilePreview
import xyz.thewind.panx.service.FileService
import jakarta.validation.Valid
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRange
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/files")
class FileApiController(
    private val currentUserService: CurrentUserService,
    private val fileService: FileService,
    private val downloadTaskService: DownloadTaskService,
) {

    @GetMapping
    fun list(
        authentication: Authentication,
        @RequestParam(required = false) parentId: Long?,
    ): ApiResponse<FileListResponse> = ApiResponse.ok(
        fileService.listNodes(currentUserService.getCurrentUser(authentication), parentId),
    )

    @GetMapping("/search")
    fun search(
        authentication: Authentication,
        @RequestParam keyword: String,
    ): ApiResponse<List<FileNodeResponse>> = ApiResponse.ok(
        fileService.search(currentUserService.getCurrentUser(authentication), keyword),
    )

    @GetMapping("/tree")
    fun tree(
        authentication: Authentication,
        @RequestParam(required = false) excludeNodeId: Long?,
    ): ApiResponse<List<FolderOptionResponse>> = ApiResponse.ok(
        fileService.listFolders(currentUserService.getCurrentUser(authentication), excludeNodeId),
    )

    @PostMapping("/folders")
    fun createFolder(
        authentication: Authentication,
        @Valid @RequestBody request: CreateFolderRequest,
    ): ApiResponse<FileNodeResponse> = ApiResponse.ok(
        fileService.createFolder(currentUserService.getCurrentUser(authentication), request.parentId, request.name),
        message = "Folder created",
    )

    @PostMapping("/upload")
    fun upload(
        authentication: Authentication,
        @RequestParam(required = false) parentId: Long?,
        @RequestPart("files") files: List<MultipartFile>,
        @RequestParam(required = false) relativePaths: List<String>?,
    ): ApiResponse<List<FileNodeResponse>> = ApiResponse.ok(
        fileService.upload(currentUserService.getCurrentUser(authentication), parentId, files, relativePaths),
        message = "Upload completed",
    )

    @PostMapping("/text")
    fun createTextFile(
        authentication: Authentication,
        @Valid @RequestBody request: CreateTextFileRequest,
    ): ApiResponse<FileNodeResponse> = ApiResponse.ok(
        fileService.createTextFile(
            currentUserService.getCurrentUser(authentication),
            request.parentId,
            request.name,
            request.content,
        ),
        message = "Text file created",
    )

    @GetMapping("/download-tasks")
    fun listDownloadTasks(
        authentication: Authentication,
    ): ApiResponse<List<DownloadTaskResponse>> = ApiResponse.ok(
        downloadTaskService.listTasks(currentUserService.getCurrentUser(authentication)),
    )

    @PostMapping("/download-tasks")
    fun createDownloadTask(
        authentication: Authentication,
        @Valid @RequestBody request: CreateRemoteDownloadTaskRequest,
    ): ApiResponse<DownloadTaskResponse> = ApiResponse.ok(
        downloadTaskService.createTask(currentUserService.getCurrentUser(authentication), request),
        message = "Download task created",
    )

    @DeleteMapping("/download-tasks/{id}")
    fun cancelDownloadTask(
        authentication: Authentication,
        @PathVariable id: Long,
    ): ApiResponse<Unit> {
        downloadTaskService.cancelTask(currentUserService.getCurrentUser(authentication), id)
        return ApiResponse.ok(message = "Download task cancelled")
    }

    @GetMapping("/{id}/download")
    fun download(
        authentication: Authentication,
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        val node = fileService.getFileForDownload(currentUserService.getCurrentUser(authentication), id)
        val resource = fileService.loadResource(node)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(node.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(node.name, StandardCharsets.UTF_8).build().toString(),
            )
            .body(resource)
    }

    @GetMapping("/{id}/preview")
    fun preview(
        authentication: Authentication,
        @PathVariable id: Long,
        @RequestHeader(name = HttpHeaders.RANGE, required = false) rangeHeader: String?,
    ): ResponseEntity<*> {
        return when (val preview = fileService.getFileForPreview(currentUserService.getCurrentUser(authentication), id)) {
            is FilePreview.Binary -> binaryPreviewResponse(preview, rangeHeader)

            is FilePreview.Text -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(preview.node.contentType ?: MediaType.TEXT_PLAIN_VALUE))
                .body(preview.content)
        }
    }

    @PatchMapping("/{id}/rename")
    fun rename(
        authentication: Authentication,
        @PathVariable id: Long,
        @Valid @RequestBody request: RenameNodeRequest,
    ): ApiResponse<FileNodeResponse> = ApiResponse.ok(
        fileService.rename(currentUserService.getCurrentUser(authentication), id, request.name),
        message = "Rename completed",
    )

    @PatchMapping("/{id}/move")
    fun move(
        authentication: Authentication,
        @PathVariable id: Long,
        @Valid @RequestBody request: MoveNodeRequest,
    ): ApiResponse<FileNodeResponse> = ApiResponse.ok(
        fileService.move(currentUserService.getCurrentUser(authentication), id, request.targetParentId),
        message = "Move completed",
    )

    @DeleteMapping("/{id}")
    fun delete(
        authentication: Authentication,
        @PathVariable id: Long,
        @RequestParam(defaultValue = "false") recursive: Boolean,
    ): ApiResponse<Unit> {
        fileService.delete(currentUserService.getCurrentUser(authentication), id, recursive)
        return ApiResponse.ok(message = "Delete completed")
    }

    private fun binaryPreviewResponse(preview: FilePreview.Binary, rangeHeader: String?): ResponseEntity<*> {
        val contentType = MediaType.parseMediaType(preview.node.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE)
        val disposition = ContentDisposition.inline().filename(preview.node.name, StandardCharsets.UTF_8).build().toString()
        val isPlayerMedia = fileService.isPlayerMedia(preview.node)
        if (!isPlayerMedia || rangeHeader.isNullOrBlank()) {
            return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(preview.resource)
        }

        val contentLength = preview.resource.contentLength()
        val range = HttpRange.parseRanges(rangeHeader).firstOrNull()
            ?: return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(preview.resource)
        val start = range.getRangeStart(contentLength)
        val requestedEnd = range.getRangeEnd(contentLength)
        val end = minOf(requestedEnd, start + PLAYER_REGION_SIZE - 1, contentLength - 1)
        val regionLength = end - start + 1
        val bytes = preview.resource.inputStream.use { input ->
            skipFully(input, start)
            input.readNBytes(regionLength.toInt())
        }
        return ResponseEntity.status(206)
            .contentType(contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header(HttpHeaders.CONTENT_RANGE, "bytes $start-$end/$contentLength")
            .contentLength(bytes.size.toLong())
            .body(ByteArrayResource(bytes))
    }

    companion object {
        private const val PLAYER_REGION_SIZE = 1_048_576L
    }

    private fun skipFully(input: java.io.InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() == -1) {
                    break
                }
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }
}
