package xyz.thewind.panx.dto

import java.time.Instant

data class ApiError(
    val code: String,
    val message: String,
    val path: String,
    val timestamp: Instant = Instant.now(),
    val details: Map<String, String?> = emptyMap(),
)
