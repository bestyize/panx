package xyz.thewind.panx.exception

import xyz.thewind.panx.dto.ApiError
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody

@ControllerAdvice(basePackages = ["xyz.thewind.panx.controller.api"])
@ResponseBody
class GlobalExceptionHandler {

    @ExceptionHandler(PanxException::class)
    fun handlePanxException(ex: PanxException, request: HttpServletRequest): ResponseEntity<ApiError> = ResponseEntity
        .status(statusFor(ex.code))
        .body(
            ApiError(
                code = ex.code,
                message = ex.message,
                path = request.requestURI,
            ),
        )

    @ExceptionHandler(MethodArgumentNotValidException::class, BindException::class, ConstraintViolationException::class)
    fun handleValidationException(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> = ResponseEntity
        .badRequest()
        .body(
            ApiError(
                code = "VALIDATION_ERROR",
                message = "Request validation failed",
                path = request.requestURI,
            ),
        )

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(ex: AuthenticationException, request: HttpServletRequest): ResponseEntity<ApiError> = ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(
            ApiError(
                code = "UNAUTHORIZED",
                message = ex.message ?: "Authentication failed",
                path = request.requestURI,
            ),
        )

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException, request: HttpServletRequest): ResponseEntity<ApiError> = ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(
            ApiError(
                code = "FORBIDDEN",
                message = ex.message ?: "Access denied",
                path = request.requestURI,
            ),
        )

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> = ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            ApiError(
                code = "INTERNAL_ERROR",
                message = ex.message ?: "Unexpected server error",
                path = request.requestURI,
            ),
        )

    private fun statusFor(code: String): HttpStatus = when (code) {
        "BAD_REQUEST" -> HttpStatus.BAD_REQUEST
        "NOT_FOUND" -> HttpStatus.NOT_FOUND
        "CONFLICT" -> HttpStatus.CONFLICT
        "FORBIDDEN" -> HttpStatus.FORBIDDEN
        "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED
        else -> HttpStatus.BAD_REQUEST
    }
}
