package xyz.thewind.panx.controller.page

import xyz.thewind.panx.service.CurrentUserService
import xyz.thewind.panx.service.FileService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@Controller
class PreviewPageController(
    private val currentUserService: CurrentUserService,
    private val fileService: FileService,
) {

    @GetMapping("/app/files/{id}/preview")
    fun preview(
        authentication: Authentication,
        @PathVariable id: Long,
        model: Model,
    ): String {
        val owner = currentUserService.getCurrentUser(authentication)
        val preview = fileService.getFileForPreview(owner, id)
        val response = fileService.toResponse(preview.node)
        model.addAttribute("file", response)
        model.addAttribute("previewUrl", "/api/files/${response.id}/preview")
        model.addAttribute("downloadUrl", "/api/files/${response.id}/download")
        model.addAttribute("backUrl", preview.node.parentId?.let { "/app/files?parentId=$it" } ?: "/app/files")
        return "preview/show"
    }
}
