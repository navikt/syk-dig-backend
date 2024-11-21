package no.nav.sykdig.digitalisering.helsenett.client

import net.logstash.logback.argument.StructuredArguments
import no.nav.sykdig.LoggingMeta
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.exceptions.SykmelderNotFoundException
import no.nav.sykdig.securelog
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class SmtssClient(
    @Value("\${smtss.url}") private val smtssUrl: String,
    private val smtssM2mRestTemplate: RestTemplate
    ) {

    val log = applog()
    val securelog = securelog()

    suspend fun findBestTssInfotrygd(
        samhandlerFnr: String,
        samhandlerOrgName: String,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ): String {

        val headers = HttpHeaders()
        headers["samhandlerFnr"] = samhandlerFnr
        headers["samhandlerOrgName"] = samhandlerOrgName
        headers["requestId"] = sykmeldingId

        // antakelse om at exceptions blir plukket opp av global exceptionhandler
        // vi nullchecker hpr tidligere i løpet
        val response =
            smtssM2mRestTemplate.exchange(
                "$smtssUrl/api/v1/samhandler/infotrygd",
                HttpMethod.GET,
                HttpEntity<Any>(headers),
                String::class.java,
            )
        if (response.statusCode.is2xxSuccessful) return response.body
        log.info(
            "smtss responded with an error code {} for {}",
            response.statusCode,
            StructuredArguments.fields(loggingMeta),
        )
        throw SykmelderNotFoundException("Samhandlerpraksis ikke funnet for samhandlerOrgname ${samhandlerOrgName}")
    }



}