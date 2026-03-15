package xyz.thewind.panx.controller.page

import xyz.thewind.panx.service.CurrentUserService
import xyz.thewind.panx.service.DownloadTaskService
import xyz.thewind.panx.service.FileService
import xyz.thewind.panx.service.ShareService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class FilePageController(
    private val currentUserService: CurrentUserService,
    private val fileService: FileService,
    private val shareService: ShareService,
    private val downloadTaskService: DownloadTaskService,
) {

    @GetMapping("/app/files")
    fun files(
        authentication: Authentication,
        model: Model,
        @RequestParam(required = false) parentId: Long?,
        @RequestParam(required = false) keyword: String?,
    ): String {
        val currentUser = currentUserService.getCurrentUser(authentication)
        val listing = fileService.listNodes(currentUser, parentId)
        val searchResults = keyword?.takeIf { it.isNotBlank() }?.let { fileService.search(currentUser, it) } ?: emptyList()

        model.addAttribute("listing", listing)
        model.addAttribute("searchResults", searchResults)
        model.addAttribute("keyword", keyword ?: "")
        model.addAttribute("isSearchMode", keyword?.isNotBlank() == true)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("activeNav", "files")
        return "files/index"
    }

    @GetMapping("/app/shares")
    fun shares(
        authentication: Authentication,
        model: Model,
    ): String {
        val currentUser = currentUserService.getCurrentUser(authentication)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("activeNav", "shares")
        model.addAttribute("activeShares", shareService.listShares(currentUser))
        return "shares/index"
    }

    @GetMapping("/app/downloads")
    fun downloads(
        authentication: Authentication,
        model: Model,
    ): String {
        val currentUser = currentUserService.getCurrentUser(authentication)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("activeNav", "downloads")
        model.addAttribute("downloadTasks", downloadTaskService.listTasks(currentUser))
        return "downloads/index"
    }
}
