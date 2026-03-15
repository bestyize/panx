package xyz.thewind.panx.controller.api

import xyz.thewind.panx.dto.ApiResponse
import xyz.thewind.panx.dto.AuthMeResponse
import xyz.thewind.panx.dto.LoginRequest
import xyz.thewind.panx.service.CurrentUserService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthApiController(
    private val authenticationManager: AuthenticationManager,
    private val currentUserService: CurrentUserService,
) {

    private val securityContextRepository = HttpSessionSecurityContextRepository()
    private val logoutHandler = SecurityContextLogoutHandler()

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): ApiResponse<AuthMeResponse> {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.username, request.password),
        )
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, httpRequest, httpResponse)

        val user = currentUserService.getCurrentUser(authentication)
        return ApiResponse.ok(
            AuthMeResponse(
                id = user.id!!,
                username = user.username,
                displayName = user.displayName,
            ),
            message = "Login successful",
        )
    }

    @PostMapping("/logout")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ApiResponse<Unit> {
        logoutHandler.logout(request, response, SecurityContextHolder.getContext().authentication)
        return ApiResponse.ok(message = "Logout successful")
    }

    @GetMapping("/me")
    fun me(): ApiResponse<AuthMeResponse> {
        val user = currentUserService.getCurrentUser()
        return ApiResponse.ok(
            AuthMeResponse(
                id = user.id!!,
                username = user.username,
                displayName = user.displayName,
            ),
        )
    }
}
