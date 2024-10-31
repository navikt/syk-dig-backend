package no.nav.sykdig.digitalisering.papirsykmelding.api

import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.AvvisSykmeldingRequest
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.SmRegistreringManuell
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Sykmelder
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
import org.springframework.web.util.UriComponentsBuilder

@Component
class SmregistreringClient(
    @Value("\${smregistrering.url}") private val url: String,
    val smregisteringRestTemplate: RestTemplate,
) {
    val log = applog()

    @Retryable
    fun postAvvisOppgaveRequest(
        authorization: String,
        oppgaveId: String,
        navEnhet: String,
        avvisSykmeldingReason: String?,
    ): ResponseEntity<HttpStatusCode> {
        val headers = HttpHeaders()
        headers.set("X-Nav-Enhet", navEnhet)
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(removeBearerPrefix(authorization))
        val uri =
            UriComponentsBuilder.fromHttpUrl("$url/api/v1/oppgave/{oppgaveId}/avvis")
                .buildAndExpand(oppgaveId)
                .toUri()

        val res =
            smregisteringRestTemplate.exchange(
                uri,
                HttpMethod.POST,
                HttpEntity(AvvisSykmeldingRequest(avvisSykmeldingReason), headers),
                HttpStatusCode::class.java,
            )

        log.info("Oppgave $oppgaveId avvist med responskode ${res.statusCode}")
        return res
    }

    @Retryable
    fun getOppgaveRequest(
        authorization: String,
        oppgaveId: String,
    ): ResponseEntity<PapirManuellOppgave> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(removeBearerPrefix(authorization))
        val uri =
            UriComponentsBuilder.fromHttpUrl("$url/api/v1/oppgave/{oppgaveId}")
                .buildAndExpand(oppgaveId)
                .toUri()

        val res =
            smregisteringRestTemplate.exchange(
                uri,
                HttpMethod.GET,
                HttpEntity<String>(headers),
                PapirManuellOppgave::class.java,
            )
        return res
    }

    @Retryable
    fun getSykmelderRequest(
        authorization: String,
        hprNummer: String,
    ): ResponseEntity<Sykmelder> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(removeBearerPrefix(authorization))

        val uri =
            UriComponentsBuilder.fromHttpUrl("$url/api/v1/sykmelder/{hprNummer}")
                .buildAndExpand(hprNummer)
                .toUri()

        return smregisteringRestTemplate.exchange(
            uri,
            HttpMethod.GET,
            HttpEntity<String>(headers),
            Sykmelder::class.java,
        )
    }

    @Retryable
    fun postSendOppgaveRequest(
        authorization: String,
        oppgaveId: String,
        navEnhet: String,
        papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.set("X-Nav-Enhet", navEnhet)
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(removeBearerPrefix(authorization))
        val uri =
            UriComponentsBuilder.fromHttpUrl("$url/api/v1/oppgave/{oppgaveId}/send")
                .buildAndExpand(oppgaveId)
                .toUri()

        val res =
            smregisteringRestTemplate.exchange(
                uri,
                HttpMethod.POST,
                HttpEntity(papirSykmelding, headers),
                String::class.java,
            )
        log.info("registrering av oppgave $oppgaveId fikk følgende responskode ${res.statusCode}")
        return res
    }

    @Retryable
    fun getFerdigstiltSykmeldingRequest(
        authorization: String,
        sykmeldingId: String,
    ): ResponseEntity<PapirManuellOppgave> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(removeBearerPrefix(authorization))
        val uri =
            UriComponentsBuilder.fromHttpUrl("$url/api/v1/sykmelding/{sykmeldingId}/ferdigstilt")
                .buildAndExpand(sykmeldingId)
                .toUri()

        return smregisteringRestTemplate.exchange(
            uri,
            HttpMethod.GET,
            HttpEntity<String>(headers),
            PapirManuellOppgave::class.java,
        )
    }

    @Retryable
    fun postOppgaveTilGosysRequest(
        authorization: String,
        oppgaveId: String,
    ): ResponseEntity<HttpStatusCode> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(removeBearerPrefix(authorization))
        val uri =
            UriComponentsBuilder.fromHttpUrl("$url/api/v1/oppgave/{oppgaveId}/tilgosys")
                .buildAndExpand(oppgaveId)
                .toUri()

        val res =
            smregisteringRestTemplate.exchange(
                uri,
                HttpMethod.POST,
                HttpEntity(null, headers),
                HttpStatusCode::class.java,
            )
        log.info("Oppgave $oppgaveId sendt til Gosys med responskode ${res.statusCode}")
        return res
    }

    @Retryable
    fun postKorrigerSykmeldingRequest(
        authorization: String,
        sykmeldingId: String,
        navEnhet: String,
        papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.set("X-Nav-Enhet", navEnhet)
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(removeBearerPrefix(authorization))
        val uri =
            UriComponentsBuilder.fromHttpUrl("$url/api/v1/sykmelding/{sykmeldingId}")
                .buildAndExpand(sykmeldingId)
                .toUri()
        val res =
            smregisteringRestTemplate.exchange(
                uri,
                HttpMethod.POST,
                HttpEntity(papirSykmelding, headers),
                String::class.java,
            )
        log.info("Korrigering av sykmelding $sykmeldingId fikk følgende responskode ${res.statusCode}")
        return res
    }

    @Retryable
    fun getRegisterPdfRequest(
        authorization: String,
        oppgaveId: String,
        dokumentInfoId: String,
    ): ResponseEntity<ByteArray> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_PDF
        headers.setBearerAuth(removeBearerPrefix(authorization))
        val uri =
            UriComponentsBuilder.fromHttpUrl("$url/api/v1/pdf/$oppgaveId/$dokumentInfoId")
                .buildAndExpand(oppgaveId, dokumentInfoId)
                .toUri()

        val response =
            smregisteringRestTemplate.exchange(
                uri,
                HttpMethod.GET,
                HttpEntity<String>(headers),
                ByteArray::class.java,
            )
        log.info("Hentet pdf for oppgave $oppgaveId og dokumentinfoId $dokumentInfoId med responskode ${response.statusCode}")
        return response
    }

    fun removeBearerPrefix(authorization: String): String {
        return authorization.removePrefix("Bearer ")
    }
}
