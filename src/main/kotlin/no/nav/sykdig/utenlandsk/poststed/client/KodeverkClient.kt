package no.nav.sykdig.utenlandsk.poststed.client

import java.time.LocalDate
import java.util.UUID
import no.nav.sykdig.shared.applog
import no.nav.sykdig.utenlandsk.poststed.PostInformasjon
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class KodeverkClient(
    @Value("\${kodeverk.url}") private val url: String,
    private val kodeverkRestTemplate: RestTemplate,
) {
    val log = applog()

    @Retryable
    fun hentKodeverk(callId: UUID): List<PostInformasjon> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        headers["Nav-Call-Id"] = callId.toString()
        headers["Nav-Consumer-Id"] = "syk-dig-backend"

        try {
            val response =
                kodeverkRestTemplate.exchange(
                    "$url/api/v1/kodeverk/Postnummer/koder/betydninger?ekskluderUgyldige=true&oppslagsdato=${LocalDate.now()}&spraak=nb",
                    HttpMethod.GET,
                    HttpEntity<Any>(headers),
                    GetKodeverkKoderBetydningerResponse::class.java,
                )
            return response.body?.toPostInformasjonListe()
                ?: throw RuntimeException("Ingen respons fra kodeverk")
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av postinformasjon fra kodeverk: ${e.message}", e)
            throw RuntimeException("Noe gikk galt ved henting av postinformasjon fra kodeverk")
        }
    }
}

data class GetKodeverkKoderBetydningerResponse(val betydninger: Map<String, List<Betydning>>) {
    fun toPostInformasjonListe(): List<PostInformasjon> {
        return betydninger.map {
            PostInformasjon(
                postnummer = it.key,
                poststed =
                    it.value.first().beskrivelser["nb"]?.term
                        ?: throw RuntimeException("Kode ${it.key} mangler term"),
            )
        }
    }
}

data class Betydning(val beskrivelser: Map<String, Beskrivelse>)

data class Beskrivelse(val term: String)
