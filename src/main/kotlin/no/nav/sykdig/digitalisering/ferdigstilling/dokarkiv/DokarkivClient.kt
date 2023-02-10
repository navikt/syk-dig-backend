package no.nav.sykdig.digitalisering.ferdigstilling.dokarkiv

import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
import no.nav.sykdig.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class DokarkivClient(
    @Value("\${dokarkiv.url}") private val url: String,
    private val dokarkivRestTemplate: RestTemplate
) {
    val log = logger()

    fun oppdaterOgFerdigstillJournalpost(
        navnSykmelder: String?,
        land: String,
        fnr: String,
        enhet: String,
        dokumentinfoId: String,
        journalpostId: String,
        sykmeldingId: String,
        receivedSykmelding: ReceivedSykmelding
    ) {
        oppdaterJournalpost(
            navnSykmelder = navnSykmelder,
            land = land,
            fnr = fnr,
            dokumentinfoId = dokumentinfoId,
            journalpostId = journalpostId,
            sykmeldingId = sykmeldingId,
            receivedSykmelding = receivedSykmelding
        )
        ferdigstillJournalpost(
            enhet = enhet,
            journalpostId = journalpostId,
            sykmeldingId = sykmeldingId
        )
    }

    @Retryable
    private fun oppdaterJournalpost(
        navnSykmelder: String?,
        land: String,
        fnr: String,
        dokumentinfoId: String,
        journalpostId: String,
        sykmeldingId: String,
        receivedSykmelding: ReceivedSykmelding
    ) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers["Nav-Callid"] = sykmeldingId

        val body = OppdaterJournalpostRequest(
            avsenderMottaker = AvsenderMottaker(
                navn = navnSykmelder,
                land = land
            ),
            bruker = Bruker(
                id = fnr
            ),
            tittel = "Utenlandsk papirsykmelding ${getFomTomTekst(receivedSykmelding)}",
            dokumenter = listOf(
                DokumentInfo(
                    dokumentInfoId = dokumentinfoId,
                    tittel = "Utenlandsk papirsykmelding ${getFomTomTekst(receivedSykmelding)}"
                )
            )
        )
        try {
            dokarkivRestTemplate.exchange(
                "$url/$journalpostId",
                HttpMethod.PUT,
                HttpEntity(body, headers),
                String::class.java
            )
            log.info("Oppdatert journalpost $journalpostId for sykmelding $sykmeldingId")
        } catch (e: HttpClientErrorException) {
            if (e.rawStatusCode == 401 || e.rawStatusCode == 403) {
                log.warn("Veileder har ikke tilgang til å oppdatere journalpostId $journalpostId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til journalpost")
            } else {
                log.error(
                    "HttpClientErrorException med responskode ${e.rawStatusCode} fra Dokarkiv ved oppdatering: ${e.message}",
                    e
                )
            }
            throw e
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException med responskode ${e.rawStatusCode} fra Dokarkiv ved oppdatering: ${e.message}",
                e
            )
            throw e
        }
    }

    private fun getFomTomTekst(receivedSykmelding: ReceivedSykmelding) =
        "${formaterDato(receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeFOMDate().first().fom)} -" +
            " ${formaterDato(receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeTOMDate().last().tom)}"

    fun List<Periode>.sortedSykmeldingPeriodeFOMDate(): List<Periode> =
        sortedBy { it.fom }
    fun List<Periode>.sortedSykmeldingPeriodeTOMDate(): List<Periode> =
        sortedBy { it.tom }

    fun formaterDato(dato: LocalDate): String {
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        return dato.format(formatter)
    }

    @Retryable
    private fun ferdigstillJournalpost(
        enhet: String,
        journalpostId: String,
        sykmeldingId: String
    ) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers["Nav-Callid"] = sykmeldingId

        val body = FerdigstillJournalpostRequest(
            journalfoerendeEnhet = enhet
        )
        try {
            dokarkivRestTemplate.exchange(
                "$url/$journalpostId/ferdigstill",
                HttpMethod.PATCH,
                HttpEntity(body, headers),
                String::class.java
            )
            log.info("Ferdigstilt journalpost $journalpostId for sykmelding $sykmeldingId")
        } catch (e: HttpClientErrorException) {
            if (e.rawStatusCode == 401 || e.rawStatusCode == 403) {
                log.warn("Veileder har ikke tilgang til å ferdigstille journalpostId $journalpostId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til journalpost")
            } else {
                log.error(
                    "HttpClientErrorException med responskode ${e.rawStatusCode} fra Dokarkiv ved ferdigstilling: ${e.message}",
                    e
                )
            }
            throw e
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException med responskode ${e.rawStatusCode} fra Dokarkiv ved ferdigstilling: ${e.message}",
                e
            )
            throw e
        }
    }
}
