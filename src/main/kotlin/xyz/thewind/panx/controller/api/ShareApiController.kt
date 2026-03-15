package xyz.thewind.panx.controller.api

import xyz.thewind.panx.dto.ApiResponse
import xyz.thewind.panx.dto.CreateShareRequest
import xyz.thewind.panx.dto.ShareInfoResponse
import xyz.thewind.panx.dto.VerifyShareRequest
import xyz.thewind.panx.service.CurrentUserService
import xyz.thewind.panx.service.FilePreview
import xyz.thewind.panx.service.FileService
import xyz.thewind.panx.service.ShareService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/shares")
class ShareApiController(
    private val currentUserService: CurrentUserService,
    private val shareService: ShareService,
    private val fileService: FileService,
) {

    @PostMapping
    fun create(
        authentication: Authentication,
        @Valid @RequestBody request: CreateShareRequest,
    ): ApiResponse<ShareInfoResponse> = ApiResponse.ok(
        shareService.createShare(
            owner = currentUserService.getCurrentUser(authentication),
            fileNodeId = request.fileNodeId,
            expireHours = request.expireHours,
            extractCode = request.extractCode,
        ),
        message = "Share created",
    )

    @GetMapping("/{token}")
    fun info(
        @PathVariable token: String,
    ): ApiResponse<ShareInfoResponse> = ApiResponse.ok(shareService.getShareInfo(token))

    @PostMapping("/{token}/verify")
    fun verify(
        @PathVariable token: String,
        @RequestBody(required = false) request: VerifyShareRequest?,
        session: HttpSession,
    ): ApiResponse<Boolean> = ApiResponse.ok(
        shareService.verify(token, request?.code, session),
        message = "Verification successful",
    )

    @GetMapping("/{token}/download")
    fun download(
        @PathVariable token: String,
        @RequestParam(required = false) nodeId: Long?,
        @RequestParam(required = false) code: String?,
        session: HttpSession?,
        request: HttpServletRequest,
    ): ResponseEntity<Resource> {
        val node = shareService.resolveDownload(token, nodeId, code, session)
        val fileResource = fileService.loadResource(node)
        shareService.recordAccess(token, request, "DOWNLOAD")
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(node.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(node.name, StandardCharsets.UTF_8).build().toString(),
            )
            .body(fileResource)
    }

    @GetMapping("/{token}/preview")
    fun preview(
        @PathVariable token: String,
        @RequestParam(required = false) nodeId: Long?,
        @RequestParam(required = false) code: String?,
        session: HttpSession?,
        request: HttpServletRequest,
        @RequestHeader(name = HttpHeaders.RANGE, required = false) rangeHeader: String?,
    ): ResponseEntity<*> {
        val node = shareService.resolvePreview(token, nodeId, code, session)
        val response = when (val preview = fileService.getFileForPreviewByNode(node)) {
            is FilePreview.Binary -> binaryPreviewResponse(preview, rangeHeader)

            is FilePreview.Text -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(preview.node.contentType ?: MediaType.TEXT_PLAIN_VALUE))
                .body(preview.content)
        }
        shareService.recordAccess(token, request, "PREVIEW")
        return response
    }

    @DeleteMapping("/{id}")
    fun delete(
        authentication: Authentication,
        @PathVariable id: Long,
    ): ApiResponse<Unit> {
        shareService.cancelShare(currentUserService.getCurrentUser(authentication), id)
        return ApiResponse.ok(message = "Share deleted")
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
