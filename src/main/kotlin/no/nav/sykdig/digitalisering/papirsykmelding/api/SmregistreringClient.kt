package no.nav.sykdig.digitalisering.papirsykmelding.api

import no.nav.sykdig.applog
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class SmregistreringClient(
    @Value("\${smregistrering.url}") private val url: String,
    val smregisteringRestTemplate: RestTemplate,
) {
    val log = applog()

    @Retryable
    fun postAvvisOppgaveRequest(
        token: String,
        oppgaveId: String,
        navEnhet: String,
        avvisSykmeldingReason: String?,
    ): ResponseEntity<HttpStatusCode> {
        val headers = HttpHeaders()
        headers.set("X-Nav-Enhet", navEnhet)
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)

        val res =
            smregisteringRestTemplate.exchange(
                "$url/api/v1/oppgave/$oppgaveId/avvis",
                HttpMethod.POST,
                HttpEntity(AvvisSykmeldingRequest(avvisSykmeldingReason), headers),
                HttpStatusCode::class.java,
            )

        log.info("Oppgave $oppgaveId avvist med responskode ${res.statusCode}")
        return res
    }

    @Retryable
    fun getOppgaveRequest(
        token: String,
        oppgaveId: String,
    ): ResponseEntity<PapirManuellOppgave> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)

        val res =
            smregisteringRestTemplate.exchange(
                "$url/api/v1/oppgave/$oppgaveId",
                HttpMethod.GET,
                HttpEntity<String>(headers),
                PapirManuellOppgave::class.java,
            )
        return res
    }

    @Retryable
    fun getPasientNavnRequest(
        token: String,
        fnr: String,
    ): ResponseEntity<PasientNavn> {
        val headers = HttpHeaders()
        headers.set("X-Pasient-Fnr", fnr)
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)

        return smregisteringRestTemplate.exchange(
            "$url/api/v1/pasient",
            HttpMethod.GET,
            HttpEntity<String>(headers),
            PasientNavn::class.java,
        )
    }

    @Retryable
    fun getSykmelderRequest(
        token: String,
        hprNummer: String,
    ): ResponseEntity<Sykmelder> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)

        return smregisteringRestTemplate.exchange(
            "$url/api/v1/sykmelder/$hprNummer",
            HttpMethod.GET,
            HttpEntity<String>(headers),
            Sykmelder::class.java,
        )
    }

    @Retryable
    fun postSendOppgaveRequest(
        token: String,
        oppgaveId: String,
        navEnhet: String,
        papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.set("X-Nav-Enhet", navEnhet)
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)

        val res =
            smregisteringRestTemplate.exchange(
                "$url/api/v1/oppgave/$oppgaveId/send",
                HttpMethod.POST,
                HttpEntity(papirSykmelding, headers),
                String::class.java,
            )
        log.info("registrering av oppgave $oppgaveId fikk følgende responskode ${res.statusCode}")
        return res
    }

    @Retryable
    fun getFerdigstiltSykmeldingRequest(
        token: String,
        sykmeldingId: String,
    ): ResponseEntity<PapirManuellOppgave> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)

        return smregisteringRestTemplate.exchange(
            "$url/api/v1/sykmelding/$sykmeldingId/ferdigstilt",
            HttpMethod.GET,
            HttpEntity<String>(headers),
            PapirManuellOppgave::class.java,
        )
    }

    @Retryable
    fun postOppgaveTilGosysRequest(
        token: String,
        oppgaveId: String,
    ): ResponseEntity<HttpStatusCode> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)

        val res =
            smregisteringRestTemplate.exchange(
                "$url/api/v1/oppgave/$oppgaveId/tilgosys",
                HttpMethod.POST,
                HttpEntity(null, headers),
                HttpStatusCode::class.java,
            )
        log.info("Oppgave $oppgaveId sendt til Gosys med responskode ${res.statusCode}")
        return res
    }

    @Retryable
    fun postKorrigerSykmeldingRequest(
        token: String,
        sykmeldingId: String,
        navEnhet: String,
        papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.set("X-Nav-Enhet", navEnhet)
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)

        val res =
            smregisteringRestTemplate.exchange(
                "$url/api/v1/sykmelding/$sykmeldingId",
                HttpMethod.POST,
                HttpEntity(papirSykmelding, headers),
                String::class.java,
            )
        log.info("Korrigering av sykmelding $sykmeldingId fikk følgende responskode ${res.statusCode}")
        return res
    }

    @Retryable
    fun getRegisterPdfRequest(
        token: String,
        oppgaveId: String,
        dokumentInfoId: String,
    ): ResponseEntity<ByteArray> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_PDF
        headers.setBearerAuth(token)

        val response =
            smregisteringRestTemplate.exchange(
                "$url/api/v1/pdf/$oppgaveId/$dokumentInfoId",
                HttpMethod.GET,
                HttpEntity<String>(headers),
                ByteArray::class.java,
            )
        return response
    }
}
