package xyz.thewind.panx.exception

open class PanxException(
    val code: String,
    override val message: String,
) : RuntimeException(message)

class NotFoundException(message: String) : PanxException("NOT_FOUND", message)

class BadRequestException(message: String) : PanxException("BAD_REQUEST", message)

class ConflictException(message: String) : PanxException("CONFLICT", message)

class ForbiddenException(message: String) : PanxException("FORBIDDEN", message)

class UnauthorizedException(message: String) : PanxException("UNAUTHORIZED", message)
