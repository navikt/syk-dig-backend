package no.nav.sykdig.config

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import no.nav.sykdig.applog
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class CustomErrorController : ErrorController {
    private val log = applog()

    @RequestMapping("/error")
    fun handleError(request: HttpServletRequest) {
        val status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)
        if (status != null) {
            val statusCode = Integer.valueOf(status.toString())
            if (statusCode == HttpStatus.BAD_REQUEST.value()) {
                log.error("Request to endpoint resulted in 400 error at  " + request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI))
                return
            }
            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                log.error("Request to endpoint resulted in 404 error at  " + request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI))
                return
            }
            if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                log.error("Request to endpoint resulted in 500 error at  " + request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI))
                return
            }
        }
        log.error("Request to endpoint resulted in error at  " + request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI))
    }
}
