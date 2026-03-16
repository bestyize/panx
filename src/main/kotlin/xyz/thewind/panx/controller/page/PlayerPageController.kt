package xyz.thewind.panx.controller.page

import xyz.thewind.panx.service.CurrentUserService
import xyz.thewind.panx.service.FileService
import xyz.thewind.panx.service.MediaAccessTokenService
import xyz.thewind.panx.service.ShareService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

@Controller
class PlayerPageController(
    private val currentUserService: CurrentUserService,
    private val fileService: FileService,
    private val shareService: ShareService,
    private val mediaAccessTokenService: MediaAccessTokenService,
) {

    @GetMapping("/app/files/{id}/player")
    fun filePlayer(
        authentication: Authentication,
        @PathVariable id: Long,
        model: Model,
    ): String {
        val owner = currentUserService.getCurrentUser(authentication)
        val node = fileService.getFileForDownload(owner, id)
        if (!fileService.isPlayerMedia(node)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported player media")
        }
        val response = fileService.toResponse(node)
        val mediaToken = mediaAccessTokenService.issueFilePreviewToken(owner.id!!, response.id)
        model.addAttribute("media", response)
        model.addAttribute("streamUrl", "/api/files/${response.id}/preview?mediaToken=$mediaToken")
        model.addAttribute("downloadUrl", "/api/files/${response.id}/download")
        model.addAttribute("backUrl", node.parentId?.let { "/app/files?parentId=$it" } ?: "/app/files")
        model.addAttribute("pageTitle", response.name)
        model.addAttribute("isSharePlayer", false)
        return "player/show"
    }

    @GetMapping("/share/{token}/player")
    fun sharePlayer(
        @PathVariable token: String,
        model: Model,
        request: HttpServletRequest,
        session: HttpSession?,
        @RequestParam(required = false) nodeId: Long?,
    ): String {
        val share = shareService.getShareInfo(token)
        val node = shareService.resolvePreview(token, nodeId, null, session)
        if (!fileService.isPlayerMedia(node)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported player media")
        }
        val response = fileService.toResponse(node)
        shareService.recordAccess(token, request, "PLAYER_VIEW")
        val backUrl = if (nodeId != null) {
            node.parentId?.let { "/share/$token?parentId=$it" } ?: "/share/$token"
        } else {
            "/share/$token"
        }
        model.addAttribute("media", response)
        model.addAttribute("streamUrl", nodeId?.let { "/api/shares/$token/preview?nodeId=$it" } ?: "/api/shares/$token/preview")
        model.addAttribute("downloadUrl", nodeId?.let { "/api/shares/$token/download?nodeId=$it" } ?: "/api/shares/$token/download")
        model.addAttribute("backUrl", backUrl)
        model.addAttribute("pageTitle", response.name)
        model.addAttribute("isSharePlayer", true)
        model.addAttribute("share", share)
        return "player/show"
    }
}
