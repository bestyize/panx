package xyz.thewind.panx.config

import xyz.thewind.panx.domain.UserAccount
import xyz.thewind.panx.service.UserAccountService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class InitAdminRunner(
    private val securityProperties: PanxSecurityProperties,
    private val userAccountService: UserAccountService,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val initAdmin = securityProperties.initAdmin
        if (!initAdmin.enabled || userAccountService.findByUsername(initAdmin.username) != null) {
            return
        }

        val now = Instant.now()
        val rawPassword = requireNotNull(initAdmin.password) { "panx.security.init-admin.password must not be null" }
        val displayName = requireNotNull(initAdmin.displayName) { "panx.security.init-admin.display-name must not be null" }
        val encodedPassword = requireNotNull(passwordEncoder.encode(rawPassword)) { "Password encoder returned null" }
        userAccountService.save(
            UserAccount(
                username = initAdmin.username,
                passwordHash = encodedPassword,
                displayName = displayName,
                enabled = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
        logger.info("Initialized default admin user '{}'", initAdmin.username)
    }
}
