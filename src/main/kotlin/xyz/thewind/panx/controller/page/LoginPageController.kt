package xyz.thewind.panx.controller.page

import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class LoginPageController {

    @GetMapping("/login")
    fun login(
        authentication: Authentication?,
        model: Model,
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false) logout: String?,
    ): String {
        if (authentication?.isAuthenticated == true && authentication.name != "anonymousUser") {
            return "redirect:/app/files"
        }
        model.addAttribute("hasError", error != null)
        model.addAttribute("loggedOut", logout != null)
        return "login"
    }
}
