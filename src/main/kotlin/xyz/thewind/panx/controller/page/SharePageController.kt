package xyz.thewind.panx.controller.page

import xyz.thewind.panx.service.ShareService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@Controller
class SharePageController(
    private val shareService: ShareService,
) {

    @GetMapping("/share/{token}")
    fun share(
        @PathVariable token: String,
        model: Model,
        request: HttpServletRequest,
        session: HttpSession?,
        @RequestParam(required = false) parentId: Long?,
    ): String {
        val share = shareService.getShareInfo(token)
        val verified = shareService.isVerified(token, session)
        shareService.recordAccess(token, request, "VIEW")
        model.addAttribute("share", share)
        model.addAttribute("shareVerified", verified)
        if (share.nodeType.name == "FOLDER") {
            if (verified) {
                runCatching { shareService.getFolderView(token, parentId, session) }
                    .onSuccess { model.addAttribute("folderView", it) }
                    .onFailure { model.addAttribute("folderViewError", it.message) }
            } else {
                model.addAttribute("folderViewError", "验证提取码后可浏览当前文件夹。")
            }
        }
        return "shares/show"
    }
}
