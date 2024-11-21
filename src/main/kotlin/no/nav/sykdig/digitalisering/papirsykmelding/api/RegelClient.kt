package no.nav.sykdig.digitalisering.papirsykmelding.api

import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.sykmelding.ReceivedSykmelding
import no.nav.sykdig.digitalisering.sykmelding.ValidationResult
import no.nav.sykdig.securelog
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange


@Component
class RegelClient(
    @Value("\${regel.url]") private val regelUrl: String,
    private val regeltM2mRestTemplate: RestTemplate
){
    val log = applog()
    val securelog = securelog()

    fun valider(sykmelding: ReceivedSykmelding, msgId: String): ValidationResult {
        log.info("validating against rules {}", kv("sykmeldingId", sykmelding.sykmelding.id))
        val headers = HttpHeaders()
        headers["Nav-CallId"] = msgId

        val response = regeltM2mRestTemplate.exchange(
            "$regelUrl/api/v2/rules/validate",
            HttpMethod.POST,
            HttpEntity(sykmelding, headers),
            ValidationResult::class.java
        )
        return response.body ?: throw HttpClientErrorException(HttpStatus.NOT_FOUND, "regelvalidering feilet for sykmeldingId ${sykmelding.sykmelding.id}")
    }
}
