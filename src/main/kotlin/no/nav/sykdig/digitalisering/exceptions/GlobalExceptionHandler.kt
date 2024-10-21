package no.nav.sykdig.digitalisering.exceptions

import no.nav.sykdig.applog
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {
    val log = applog()

    @ExceptionHandler(HttpClientErrorException::class)
    fun handleHttpClientErrorException(e: HttpClientErrorException): ResponseEntity<ErrorResponse> {
        return when (e.statusCode) {
            HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN -> {
                log.error("Access denied. Status: ${e.statusCode}. Message: ${e.message}")
                ResponseEntity.status(e.statusCode).body(ErrorResponse("Veileder har ikke tilgang til oppgaven.", e.statusCode.value(), LocalDateTime.now()))
            }
            HttpStatus.BAD_REQUEST -> {
                log.error("Bad request. Status: ${e.statusCode}. Message: ${e.responseBodyAsString}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(e.responseBodyAsString, e.statusCode.value(), LocalDateTime.now()))
            }
            HttpStatus.NOT_FOUND -> {
                log.error("Not found. Status: ${e.statusCode}. Message: ${e.responseBodyAsString}")
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(e.responseBodyAsString, e.statusCode.value(), LocalDateTime.now()))
            }
            HttpStatus.GONE -> {
                log.error("Gone. Status: ${e.statusCode}. Message: ${e.responseBodyAsString}")
                ResponseEntity.status(HttpStatus.GONE).body(ErrorResponse(e.responseBodyAsString, e.statusCode.value(), LocalDateTime.now()))
            }
            else -> {
                log.error("Client error. Status: ${e.statusCode}. Message: ${e.responseBodyAsString}")
                ResponseEntity.status(e.statusCode).body(ErrorResponse(e.responseBodyAsString, e.statusCode.value(), LocalDateTime.now()))
            }
        }
    }

    @ExceptionHandler(HttpServerErrorException::class)
    fun handleHttpServerErrorException(e: HttpServerErrorException): ResponseEntity<ErrorResponse> {
        log.error("Server error. Status: ${e.statusCode}. Message: ${e.message}", e)
        return ResponseEntity.status(e.statusCode).body(ErrorResponse("Internal server error occurred.", e.statusCode.value(), LocalDateTime.now()))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<String> {
        log.error("Unhandled exception: ${e.message}", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred")
    }
}

data class ErrorResponse(val message: String, val httpStatus: Int, val timestamp: LocalDateTime)
