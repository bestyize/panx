package xyz.thewind.panx.service

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class PanxUserDetailsService(
    private val userAccountService: UserAccountService,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userAccountService.findByUsername(username)
            ?: throw UsernameNotFoundException("User $username not found")

        return User(
            user.username,
            user.passwordHash,
            user.enabled,
            true,
            true,
            true,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
    }
}
