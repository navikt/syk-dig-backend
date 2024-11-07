package no.nav.sykdig.digitalisering.norskHelsenett.client

import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.norskHelsenett.Behandler
import no.nav.sykdig.digitalisering.pdl.client.graphql.PDL_QUERY
import no.nav.sykdig.securelog
import okhttp3.Headers
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class NorskHelsenettClient(
    @Value("\$helsenett.url") private val helsenettUrl: String,
    private val norskHelsenettRestTemplate: RestTemplate,
) {

    val log = applog()
    val securelog = securelog()

    fun getBehandler(hprNummer: String, callId: String, ): Behandler {
        log.info("Henter behandler fra syfohelsenettproxy for callId {}", callId)

        val headers = HttpHeaders()
        headers["Nav-CallId"] = callId
        headers["hprNummer"] = padHpr(hprNummer)

        // antakelse om at exceptions blir plukket opp av global exceptionhandler
        // vi nullchecker hpr tidligere i løpet
        val response = norskHelsenettRestTemplate.exchange(
            "$helsenettUrl/api/v2/behandlerMedHprNummer",
            HttpMethod.GET,
            HttpEntity<Any>(headers),
            Behandler::class.java
        )

        log.info("Hentet behandler for callId {}", callId)
        // antakelse om at not_found blir plukket opp av glibal excewption handler
        return response.body!!

    }


    fun padHpr(hprnummer: String): String? {
        if (hprnummer.length < 9) {
            return hprnummer.padStart(9, '0')
        }
        return hprnummer
    }

}