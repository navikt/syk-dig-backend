package no.nav.sykdig.digitalisering.exceptions

import no.nav.sykdig.applog
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException

@RestControllerAdvice
class GlobalExceptionHandler {
    val log = applog()

    @ExceptionHandler(HttpClientErrorException::class)
    fun handleHttpClientErrorException(e: HttpClientErrorException): ResponseEntity<String> {
        return when (e.statusCode) {
            HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN -> {
                log.error("Access denied. Status: ${e.statusCode}. Message: ${e.message}", e)
                ResponseEntity.status(e.statusCode).body("Veileder har ikke tilgang til oppgaven.")
            }
            HttpStatus.BAD_REQUEST -> {
                log.error("Bad request. Status: ${e.statusCode}. Message: ${e.message}", e)
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.responseBodyAsString)
            }
            HttpStatus.NOT_FOUND -> {
                log.error("Not found. Status: ${e.statusCode}. Message: ${e.message}", e)
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.responseBodyAsString)
            }
            HttpStatus.GONE -> {
                log.error("Gone. Status: ${e.statusCode}. Message: ${e.message}", e)
                ResponseEntity.status(HttpStatus.GONE).body(e.responseBodyAsString)
            }
            else -> {
                log.error("Client error. Status: ${e.statusCode}. Message: ${e.message}", e)
                ResponseEntity.status(e.statusCode).body(e.responseBodyAsString)
            }
        }
    }

    @ExceptionHandler(HttpServerErrorException::class)
    fun handleHttpServerErrorException(e: HttpServerErrorException): ResponseEntity<String> {
        log.error("Server error. Status: ${e.statusCode}. Message: ${e.message}", e)
        return ResponseEntity.status(e.statusCode).body("Internal server error occurred.")
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<String> {
        log.error("Unhandled exception: ${e.message}", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred")
    }

    @ExceptionHandler(SykmelderNotFoundException::class)
    fun handleSykmelderNotFoundException(e: SykmelderNotFoundException): ResponseEntity<String> {
        log.error("Sykmelder not found: ${e.message}", e)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Sykmelder not found")
    }

    @ExceptionHandler(MissingJournalpostException::class)
    fun handleMissingJournalpostException(e: MissingJournalpostException): ResponseEntity<String> {
        log.error("Journalpost is missing: ${e.message}", e)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Journalpost is missing")
    }

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnAuthorizedException(e: UnauthorizedException): ResponseEntity<String> {
        log.warn("Caught UnauthorizedException ${e.message}", e)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("UnauthorizedException")
    }

    @ExceptionHandler(ValidationException::class)
    fun handleValidationException(e: ValidationException): ResponseEntity<String> {
        log.error("Caught ValidationException ${e.message} validationresult ${e.validationResult}", e)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("ValidationException")
    }
}


