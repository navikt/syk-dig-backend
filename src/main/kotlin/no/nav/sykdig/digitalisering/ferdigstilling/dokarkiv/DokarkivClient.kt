package no.nav.sykdig.digitalisering.ferdigstilling.dokarkiv

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.model.Periode
import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
import no.nav.sykdig.logger
import no.nav.sykdig.model.DokumentDbModel
import no.nav.sykdig.objectMapper
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
import java.util.Locale

@Component
class DokarkivClient(
    @Value("\${dokarkiv.url}") private val url: String,
    private val dokarkivRestTemplate: RestTemplate,
) {
    val log = logger()

    fun oppdaterOgFerdigstillJournalpost(
        landAlpha3: String?,
        fnr: String,
        enhet: String,
        dokumentinfoId: String,
        journalpostId: String,
        sykmeldingId: String,
        perioder: List<Periode>?,
        source: String,
        avvist: Boolean,
    ) {
        oppdaterJournalpost(
            landAlpha3 = landAlpha3,
            fnr = fnr,
            dokumentinfoId = dokumentinfoId,
            journalpostId = journalpostId,
            sykmeldingId = sykmeldingId,
            perioder = perioder,
            source = source,
            avvist = avvist,
        )
        ferdigstillJournalpost(
            enhet = enhet,
            journalpostId = journalpostId,
            sykmeldingId = sykmeldingId,
        )
    }

    @Retryable
    private fun oppdaterJournalpost(
        landAlpha3: String?,
        fnr: String,
        dokumentinfoId: String,
        journalpostId: String,
        sykmeldingId: String,
        perioder: List<Periode>?,
        source: String,
        avvist: Boolean,
    ) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers["Nav-Callid"] = sykmeldingId

        val body = createOppdaterJournalpostRequest(landAlpha3, fnr, dokumentinfoId, perioder, source, avvist)
        try {
            dokarkivRestTemplate.exchange(
                "$url/$journalpostId",
                HttpMethod.PUT,
                HttpEntity(body, headers),
                String::class.java,
            )
            log.info("Oppdatert journalpost $journalpostId for sykmelding $sykmeldingId")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder har ikke tilgang til å oppdatere journalpostId $journalpostId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til journalpost")
            } else {
                log.error(
                    "HttpClientErrorException med responskode ${e.statusCode.value()} fra Dokarkiv ved oppdatering: ${e.message}",
                    e,
                )
            }
            throw e
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException med responskode ${e.statusCode.value()} fra Dokarkiv ved oppdatering: ${e.message}",
                e,
            )
            throw e
        }
    }

    private fun getFomTomTekst(perioder: List<Periode>) =
        "${formaterDato(perioder.sortedSykmeldingPeriodeFOMDate().first().fom)} -" +
            " ${formaterDato(perioder.sortedSykmeldingPeriodeTOMDate().last().tom)}"

    fun List<Periode>.sortedSykmeldingPeriodeFOMDate(): List<Periode> =
        sortedBy { it.fom }
    fun List<Periode>.sortedSykmeldingPeriodeTOMDate(): List<Periode> =
        sortedBy { it.tom }

    fun formaterDato(dato: LocalDate): String {
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        return dato.format(formatter)
    }

    private fun createOppdaterJournalpostRequest(
        landAlpha3: String?,
        fnr: String,
        dokumentinfoId: String,
        perioder: List<Periode>?,
        source: String,
        avvist: Boolean,
    ): OppdaterJournalpostRequest {
        if (source == "rina") {
            return OppdaterJournalpostRequest(
                avsenderMottaker = AvsenderMottaker(
                    navn = source,
                    land = if (landAlpha3 != null) { mapFromAlpha3Toalpha2(landAlpha3) } else { null },
                ),
                bruker = Bruker(
                    id = fnr,
                ),
                tittel = createTittleRina(perioder, avvist),
                dokumenter = listOf(
                    DokumentInfo(
                        dokumentInfoId = dokumentinfoId,
                        tittel = createTittleRina(perioder, avvist),
                    ),
                ),
            )
        } else {
            return OppdaterJournalpostRequest(
                avsenderMottaker = AvsenderMottaker(
                    navn = if (landAlpha3 != null) { findCountryName(landAlpha3) } else { source },
                    land = if (landAlpha3 != null) { mapFromAlpha3Toalpha2(landAlpha3) } else { null },
                ),
                bruker = Bruker(
                    id = fnr,
                ),
                tittel = createTittle(perioder, avvist),
                dokumenter = listOf(
                    DokumentInfo(
                        dokumentInfoId = dokumentinfoId,
                        tittel = createTittle(perioder, avvist),
                    ),
                ),
            )
        }
    }

    fun createTittleRina(perioder: List<Periode>?, avvist: Boolean): String {
        return if (avvist) { "Avvist Søknad om kontantytelser" } else if (perioder.isNullOrEmpty()) { "Søknad om kontantytelser" } else { "Søknad om kontantytelser ${getFomTomTekst(perioder)}" }
    }

    fun createTittle(perioder: List<Periode>?, avvist: Boolean): String {
        return if (avvist) { "Avvist Utenlandsk papirsykmelding" } else if (perioder.isNullOrEmpty()) { "Utenlandsk papirsykmelding" } else { "Utenlandsk papirsykmelding ${getFomTomTekst(perioder)}" }
    }

    fun findCountryName(landAlpha3: String): String {
        val countries: List<Country> =
            objectMapper.readValue<List<Country>>(DokarkivClient::class.java.getResourceAsStream("/country/countries-norwegian.json")!!)
        return countries.first { it.alpha3 == landAlpha3.lowercase(Locale.getDefault()) }.name
    }

    fun mapFromAlpha3Toalpha2(landAlpha3: String): String {
        val countries: List<Country> =
            objectMapper.readValue<List<Country>>(DokarkivClient::class.java.getResourceAsStream("/country/countries-norwegian.json")!!)
        return countries.first { it.alpha3 == landAlpha3.lowercase(Locale.getDefault()) }.alpha2
    }

    @Retryable
    private fun ferdigstillJournalpost(
        enhet: String,
        journalpostId: String,
        sykmeldingId: String,
    ) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers["Nav-Callid"] = sykmeldingId

        val body = FerdigstillJournalpostRequest(
            journalfoerendeEnhet = enhet,
        )
        try {
            dokarkivRestTemplate.exchange(
                "$url/$journalpostId/ferdigstill",
                HttpMethod.PATCH,
                HttpEntity(body, headers),
                String::class.java,
            )
            log.info("Ferdigstilt journalpost $journalpostId for sykmelding $sykmeldingId")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder har ikke tilgang til å ferdigstille journalpostId $journalpostId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til journalpost")
            } else {
                log.error(
                    "HttpClientErrorException med responskode ${e.statusCode.value()} fra Dokarkiv ved ferdigstilling: ${e.message}",
                    e,
                )
            }
            throw e
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException med responskode ${e.statusCode.value()} fra Dokarkiv ved ferdigstilling: ${e.message}",
                e,
            )
            throw e
        }
    }

    fun updateDocument(journalpostid: String, document: DokumentDbModel) {
        TODO("Not yet implemented")
    }
}

data class Country(
    val id: Int,
    val alpha2: String,
    val alpha3: String,
    val name: String,
)
