package dz.wave.common

import dz.wave.domain.Violation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** JSON settings shared by the API and the persistence layer. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val requestId: String? = null,
    val violations: List<Violation> = emptyList(),
    val details: JsonElement? = null,
)

open class ApiException(val code: String, message: String) : RuntimeException(message)

class ValidationException(val violations: List<Violation>) :
    ApiException("VALIDATION_FAILED", violations.joinToString("; ") { it.message })

class NotFoundException(code: String, message: String) : ApiException(code, message)

class ConflictException(code: String, message: String, val details: JsonElement? = null) : ApiException(code, message)

class UnauthorizedException(message: String = "Authentication required") : ApiException("UNAUTHORIZED", message)

class ForbiddenException(message: String = "Forbidden") : ApiException("FORBIDDEN", message)

class ServiceUnavailableException(code: String, message: String) : ApiException(code, message)
