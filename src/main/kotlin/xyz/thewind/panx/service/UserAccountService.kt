package xyz.thewind.panx.service

import xyz.thewind.panx.domain.UserAccount
import xyz.thewind.panx.exception.NotFoundException
import xyz.thewind.panx.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class UserAccountService(
    private val userRepository: UserRepository,
) {
    fun findByUsername(username: String): UserAccount? = userRepository.findByUsername(username)

    fun getByUsername(username: String): UserAccount = findByUsername(username)
        ?: throw NotFoundException("User not found")

    fun getById(id: Long): UserAccount = userRepository.findById(id).orElseThrow {
        NotFoundException("User not found")
    }

    fun save(userAccount: UserAccount): UserAccount = userRepository.save(userAccount)
}
