package no.nav.sykdig.nasjonal.clients

import no.nav.sykdig.shared.applog
import no.nav.sykdig.nasjonal.models.AvvisSykmeldingRequest
import no.nav.sykdig.nasjonal.models.PapirManuellOppgave
import no.nav.sykdig.nasjonal.models.PapirSmRegistering
import no.nav.sykdig.nasjonal.models.SmRegistreringManuell
import no.nav.sykdig.nasjonal.services.isValidOppgaveId
import no.nav.sykdig.utenlandsk.models.ReceivedSykmelding
import org.apache.kafka.common.network.Send
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime
import java.time.OffsetDateTime

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
        if(!isValidOppgaveId(oppgaveId))
            throw IllegalArgumentException("Invalid oppgaveId does not contain only alphanumerical characters. oppgaveId: $oppgaveId")
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
        log.info("Korrigering av sykmelding $sykmeldingId fikk følgende responskode ${res.statusCode} der body er ${res.body}")
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

data class SendtSykmeldingHistorySykDig(
    val id: String,
    val sykmeldingId: String,
    val ferdigstiltAv: String,
    val datoFerdigstilt: LocalDateTime?,
    val timestamp: OffsetDateTime,
    val receivedSykmelding: ReceivedSykmelding,
)


data class ManuellOppgaveDTOSykDig(
    val journalpostId: String,
    val fnr: String?,
    val aktorId: String?,
    val dokumentInfoId: String?,
    val datoOpprettet: OffsetDateTime?,
    val sykmeldingId: String,
    val oppgaveid: Int?,
    val ferdigstilt: Boolean,
    val papirSmRegistering: PapirSmRegistering?,
    var pdfPapirSykmelding: ByteArray?,
    val ferdigstiltAv: String?,
    val utfall: String?,
    val datoFerdigstilt: LocalDateTime?,
    val avvisningsgrunn: String?,
)

data class MigrationObject(
    val sykmeldingId: String,
    val manuellOppgave: MutableList<ManuellOppgaveDTOSykDig>,
    val sendtSykmeldingHistory: MutableList<SendtSykmeldingHistorySykDig>?,
)


