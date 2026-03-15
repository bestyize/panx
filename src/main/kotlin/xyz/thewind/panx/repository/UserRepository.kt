package xyz.thewind.panx.repository

import xyz.thewind.panx.domain.UserAccount
import org.springframework.data.repository.CrudRepository

interface UserRepository : CrudRepository<UserAccount, Long> {
    fun findByUsername(username: String): UserAccount?
}
