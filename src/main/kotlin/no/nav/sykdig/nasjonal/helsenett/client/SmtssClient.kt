package no.nav.sykdig.nasjonal.helsenett.client

import net.logstash.logback.argument.StructuredArguments
import no.nav.sykdig.shared.LoggingMeta
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.securelog
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

@Component
class SmtssClient(
    @Value("\${smtss.url}") private val smtssUrl: String,
    private val smtssM2mRestTemplate: RestTemplate,
) {

    val log = applog()
    val securelog = securelog()

    suspend fun findBestTssInfotrygd(
        samhandlerFnr: String,
        samhandlerOrgName: String,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ): String? {

        val headers = HttpHeaders()
        headers["samhandlerFnr"] = samhandlerFnr
        headers["samhandlerOrgName"] = samhandlerOrgName
        headers["requestId"] = sykmeldingId
        try {
            val response =
                smtssM2mRestTemplate.exchange(
                    "$smtssUrl/api/v1/samhandler/infotrygd",
                    HttpMethod.GET,
                    HttpEntity<TSSident>(headers),
                    TSSident::class.java,
                )
            if (response.statusCode.is2xxSuccessful) {
                return response.body?.tssid
            }
        } catch (httpClientErrorException: HttpClientErrorException) {
            log.warn(
                "smtss responded with an error code {} for {}",
                httpClientErrorException.statusCode,
                StructuredArguments.fields(loggingMeta),
            )
        }
        return null
    }
}

data class TSSident(val tssid: String)
