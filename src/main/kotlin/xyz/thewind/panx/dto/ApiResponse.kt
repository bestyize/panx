package xyz.thewind.panx.dto

data class ApiResponse<T>(
    val success: Boolean = true,
    val data: T? = null,
    val message: String? = null,
) {
    companion object {
        fun <T> ok(data: T? = null, message: String? = null): ApiResponse<T> = ApiResponse(
            success = true,
            data = data,
            message = message,
        )
    }
}
