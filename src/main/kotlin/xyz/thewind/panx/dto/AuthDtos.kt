package xyz.thewind.panx.dto

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank
    val username: String,
    @field:NotBlank
    val password: String,
)

data class AuthMeResponse(
    val id: Long,
    val username: String,
    val displayName: String,
)
