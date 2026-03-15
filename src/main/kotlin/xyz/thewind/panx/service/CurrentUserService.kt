package xyz.thewind.panx.service

import xyz.thewind.panx.domain.UserAccount
import xyz.thewind.panx.exception.UnauthorizedException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class CurrentUserService(
    private val userAccountService: UserAccountService,
) {
    fun getCurrentUser(authentication: Authentication? = SecurityContextHolder.getContext().authentication): UserAccount {
        val username = authentication?.name
            ?.takeIf { authentication.isAuthenticated && it != "anonymousUser" }
            ?: throw UnauthorizedException("Authentication required")
        return userAccountService.getByUsername(username)
    }
}
