package no.nav.sykdig.dokarkiv

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.reactor.awaitSingle
import no.nav.sykdig.shared.LoggingMeta
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.exceptions.IkkeTilgangException
import no.nav.sykdig.shared.Periode
import no.nav.sykdig.nasjonal.models.Sykmelder
import no.nav.sykdig.dokarkiv.model.*
import no.nav.sykdig.saf.graphql.AvsenderMottaker
import no.nav.sykdig.saf.graphql.AvsenderMottakerIdType
import no.nav.sykdig.shared.objectMapper
import no.nav.sykdig.shared.securelog
import no.nav.sykdig.shared.utils.createTitle
import no.nav.sykdig.shared.utils.createTitleNasjonal
import no.nav.sykdig.shared.utils.createTitleNavNo
import no.nav.sykdig.shared.utils.createTitleRina
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.Locale

@Component
class DokarkivClient(
    @Value("\${dokarkiv.url}") private val url: String,
    private val dokarkivWebClient: WebClient,
) {
    val log = applog()
    val securelog = securelog()

    suspend fun updateDocument(
        journalpostid: String,
        documentId: String,
        tittel: String,
    ) {
        val oppdaterDokumentRequest = OppdaterDokumentRequest(
            dokumenter = listOf(
                DokumentInfo(
                    dokumentInfoId = documentId,
                    tittel = tittel,
                )
            )
        )

        try {
            dokarkivWebClient.put()
                .uri("$url/$journalpostid")
                .bodyValue(oppdaterDokumentRequest)
                .retrieve()
                .onStatus({ it != HttpStatus.OK }) { response ->
                    Mono.error(HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Dokumentoppdatering feilet"))
                }
                .toBodilessEntity()
                .awaitSingle()
        } catch (e: Exception) {
            log.error("Feil ved oppdatering av dokument for journalpostId: $journalpostid, error: ${e.message}", e)
            throw e
        }

}

    suspend fun oppdaterOgFerdigstillUtenlandskJournalpost(
        landAlpha3: String?,
        fnr: String,
        enhet: String,
        dokumentinfoId: String?,
        journalpostId: String,
        sykmeldingId: String,
        perioder: List<Periode>?,
        source: String,
        avvisningsGrunn: String?,
        orginalAvsenderMottaker: AvsenderMottaker?,
        sykmeldtNavn: String?,
    ) {
        oppdaterUtenlandskJournalpost(
            landAlpha3 = landAlpha3,
            fnr = fnr,
            dokumentinfoId = dokumentinfoId,
            journalpostId = journalpostId,
            sykmeldingId = sykmeldingId,
            perioder = perioder,
            source = source,
            avvisningsGrunn = avvisningsGrunn,
            orginalAvsenderMottaker = orginalAvsenderMottaker,
            sykmeldtNavn = sykmeldtNavn,
        )
        ferdigstillJournalpost(
            enhet = enhet,
            journalpostId = journalpostId,
            sykmeldingId = sykmeldingId,
        )
    }

    @Retryable
    suspend private fun oppdaterUtenlandskJournalpost(
        landAlpha3: String?,
        fnr: String,
        dokumentinfoId: String?,
        journalpostId: String,
        sykmeldingId: String,
        perioder: List<Periode>?,
        source: String,
        avvisningsGrunn: String?,
        orginalAvsenderMottaker: AvsenderMottaker?,
        sykmeldtNavn: String?,
    ) {
        val body =
            createOppdaterUtenlandskJournalpostRequest(
                landAlpha3,
                fnr,
                dokumentinfoId,
                perioder,
                source,
                avvisningsGrunn,
                orginalAvsenderMottaker,
                sykmeldtNavn,
            )
        oppdaterJournalpostRequest(body, sykmeldingId, journalpostId)
    }

    @Retryable
    private suspend fun oppdaterJournalpostRequest(
        oppdaterJournalpostRequest: OppdaterJournalpostRequest,
        sykmeldingId: String,
        journalpostId: String,
    ): ResponseEntity<Void>? {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            accept = listOf(MediaType.APPLICATION_JSON)
            set("Nav-Callid", sykmeldingId)
        }

        try {
            securelog.info("createOppdaterJournalpostRequest: ${objectMapper.writeValueAsString(oppdaterJournalpostRequest)}")

            val response = dokarkivWebClient.put()
                .uri("$url/$journalpostId")
                .headers { it.addAll(headers) }
                .bodyValue(oppdaterJournalpostRequest)
                .retrieve()
                .onStatus({ it != HttpStatus.OK }) { response ->
                    Mono.error(HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Feil ved oppdatering av journalpost"))
                }
                .toBodilessEntity()
                .awaitSingle()

            log.info("Oppdatert journalpost $journalpostId for sykmelding $sykmeldingId")
            return response
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder har ikke tilgang til å oppdatere journalpostId $journalpostId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til journalpost")
            } else if (e.statusCode.value() == 400) {
                log.error("HttpClientErrorException med responskode ${e.statusCode.value()} fra Dokarkiv ved oppdatering: ${e.message}", e)
            } else {
                log.error("HttpClientErrorException med responskode ${e.statusCode.value()} fra Dokarkiv ved oppdatering: ${e.message}", e)
            }
            throw e
        } catch (e: HttpServerErrorException) {
            log.error("HttpServerErrorException med responskode ${e.statusCode.value()} fra Dokarkiv ved oppdatering: ${e.message}", e)
            throw e
        }
    }

    private fun createOppdaterUtenlandskJournalpostRequest(
        landAlpha3: String?,
        fnr: String,
        dokumentinfoId: String?,
        perioder: List<Periode>?,
        source: String,
        avvisningsGrunn: String?,
        orginalAvsenderMottaker: AvsenderMottaker?,
        sykmeldtNavn: String?,
    ): OppdaterJournalpostRequest {
        when (source) {
            "rina" -> {
                return OppdaterJournalpostRequest(
                    avsenderMottaker =
                        createAvsenderMottaker(
                            orginalAvsenderMottaker = orginalAvsenderMottaker,
                            land =
                                if (landAlpha3 != null) {
                                    mapFromAlpha3Toalpha2(landAlpha3)
                                } else {
                                    null
                                },
                            source = source,
                            sykmeldtNavn = sykmeldtNavn,
                            sykmeldtFnr = fnr,
                        ),
                    bruker =
                        DokBruker(
                            id = fnr,
                        ),
                    tittel = createTitleRina(perioder, avvisningsGrunn),
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                dokumentInfoId = dokumentinfoId,
                                tittel = createTitleRina(perioder, avvisningsGrunn),
                            ),
                        ),
                )
            }

            "navno" -> {
                return OppdaterJournalpostRequest(
                    tema = "SYK",
                    avsenderMottaker =
                        createAvsenderMottaker(
                            orginalAvsenderMottaker = orginalAvsenderMottaker,
                            land =
                                if (landAlpha3 != null) {
                                    mapFromAlpha3Toalpha2(landAlpha3)
                                } else {
                                    null
                                },
                            source = source,
                            sykmeldtNavn = sykmeldtNavn,
                            sykmeldtFnr = fnr,
                        ),
                    bruker =
                        DokBruker(
                            id = fnr,
                        ),
                    tittel = createTitleNavNo(perioder, avvisningsGrunn),
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                dokumentInfoId = dokumentinfoId,
                                tittel = createTitleNavNo(perioder, avvisningsGrunn),
                            ),
                        ),
                )
            }

            else -> {
                return OppdaterJournalpostRequest(
                    avsenderMottaker =
                        createAvsenderMottaker(
                            orginalAvsenderMottaker = orginalAvsenderMottaker,
                            land =
                                if (landAlpha3 != null) {
                                    mapFromAlpha3Toalpha2(landAlpha3)
                                } else {
                                    null
                                },
                            source = source,
                            sykmeldtNavn = sykmeldtNavn,
                            sykmeldtFnr = fnr,
                        ),
                    bruker =
                        DokBruker(
                            id = fnr,
                        ),
                    tittel = createTitle(perioder, avvisningsGrunn),
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                dokumentInfoId = dokumentinfoId,
                                tittel = createTitle(perioder, avvisningsGrunn),
                            ),
                        ),
                )
            }
        }
    }

    fun createAvsenderMottaker(
        orginalAvsenderMottaker: AvsenderMottaker?,
        land: String?,
        source: String,
        sykmeldtNavn: String?,
        sykmeldtFnr: String,
    ): AvsenderMottakerRequest {
        return AvsenderMottakerRequest(
            navn = mapNavn(orginalAvsenderMottaker, land, source, sykmeldtNavn),
            id = mapId(orginalAvsenderMottaker, sykmeldtFnr),
            idType = mapidType(orginalAvsenderMottaker?.type),
            land = land,
        )
    }

    fun mapId(
        orginalAvsenderMottaker: AvsenderMottaker?,
        sykmeldtFnr: String,
    ): String? {
        return if (orginalAvsenderMottaker != null && mapidType(orginalAvsenderMottaker.type) == IdType.FNR) {
            sykmeldtFnr
        } else if (orginalAvsenderMottaker != null && mapidType(orginalAvsenderMottaker.type) != IdType.FNR) {
            orginalAvsenderMottaker.id
        } else {
            sykmeldtFnr
        }
    }

    fun mapNavn(
        orginalAvsenderMottaker: AvsenderMottaker?,
        land: String?,
        source: String,
        sykmeldtNavn: String?,
    ): String? {
        return if (!orginalAvsenderMottaker?.navn.isNullOrBlank()) {
            orginalAvsenderMottaker?.navn
        } else if (orginalAvsenderMottaker?.type == AvsenderMottakerIdType.FNR && !sykmeldtNavn.isNullOrBlank()) {
            sykmeldtNavn
        } else if (orginalAvsenderMottaker?.type == AvsenderMottakerIdType.FNR && sykmeldtNavn.isNullOrBlank()) {
            null
        } else {
            source
        }
    }

    fun mapidType(orginalAvsenderMottakerIdType: AvsenderMottakerIdType?): IdType {
        return when (orginalAvsenderMottakerIdType) {
            AvsenderMottakerIdType.FNR -> IdType.FNR
            AvsenderMottakerIdType.HPRNR -> IdType.HPRNR
            AvsenderMottakerIdType.ORGNR -> IdType.ORGNR
            AvsenderMottakerIdType.UTL_ORG -> IdType.UTL_ORG
            else -> IdType.FNR
        }
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
    private suspend fun ferdigstillJournalpost(
        enhet: String,
        journalpostId: String,
        sykmeldingId: String,
    ): ResponseEntity<String>? {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            accept = listOf(MediaType.APPLICATION_JSON)
            set("Nav-Callid", sykmeldingId)
        }

        val body = FerdigstillJournalpostRequest(
            journalfoerendeEnhet = enhet,
        )

        try {
            val response = dokarkivWebClient.patch()
                .uri("$url/$journalpostId/ferdigstill")
                .headers { it.addAll(headers) }
                .bodyValue(body)
                .retrieve()
                .onStatus({ it != HttpStatus.OK }) { response ->
                    Mono.error(HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Feil ved ferdigstilling av journalpost"))
                }
                .toBodilessEntity()
                .awaitSingle()

            log.info("Ferdigstilt journalpost $journalpostId for sykmelding $sykmeldingId")
            return response
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder har ikke tilgang til å ferdigstille journalpostId $journalpostId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til journalpost")
            } else if (e.statusCode.value() == 400) {
                log.error("HttpClientErrorException med responskode ${e.statusCode.value()} fra Dokarkiv ved ferdigstilling: ${e.message}", e)
            } else {
                log.error("HttpClientErrorException med responskode ${e.statusCode.value()} fra Dokarkiv ved ferdigstilling: ${e.message}", e)
            }
            throw e
        } catch (e: HttpServerErrorException) {
            log.error("HttpServerErrorException med responskode ${e.statusCode.value()} fra Dokarkiv ved ferdigstilling: ${e.message}", e)
            throw e
        }
    }


    suspend fun oppdaterOgFerdigstillNasjonalJournalpost(
        journalpostId: String,
        dokumentInfoId: String? = null,
        pasientFnr: String,
        sykmeldingId: String,
        sykmelder: Sykmelder,
        loggingMeta: LoggingMeta,
        navEnhet: String,
        avvist: Boolean,
        perioder: List<Periode>?,
    ): String? {
        val oppdaterJournalpostRequest = createOppdaterJournalpostNasjonalRequest(dokumentInfoId, pasientFnr, sykmelder, avvist, perioder)
        oppdaterJournalpostRequest(oppdaterJournalpostRequest, sykmeldingId, journalpostId)

        return ferdigstillJournalpost(
            enhet = navEnhet,
            journalpostId = journalpostId,
            sykmeldingId = sykmeldingId,
        ).body
    }

    private fun createOppdaterJournalpostNasjonalRequest(
        dokumentInfoId: String?,
        pasientFnr: String,
        sykmelder: Sykmelder,
        avvist: Boolean,
        perioder: List<Periode>?,
    ): OppdaterJournalpostRequest {
        val oppdaterJournalpostRequest = OppdaterJournalpostRequest(
            avsenderMottaker = getAvsenderMottakerRequest(sykmelder),
            bruker = DokBruker(id = pasientFnr),
            sak = Sak(),
            tittel = createTitleNasjonal(perioder, avvist),
            dokumenter = if (dokumentInfoId != null) {
                listOf(
                    DokumentInfo(
                        dokumentInfoId = dokumentInfoId,
                        tittel = createTitleNasjonal(perioder, avvist),
                    ),
                )
            } else {
                null
            },
        )
        return oppdaterJournalpostRequest
    }

    private fun padHpr(hprnummer: String?): String? {
        if (hprnummer?.length != null && hprnummer.length < 9) {
            return hprnummer.padStart(9, '0')
        }
        return hprnummer
    }

    private fun getAvsenderMottakerRequest(sykmelder: Sykmelder): AvsenderMottakerRequest {
        return AvsenderMottakerRequest(
            id = padHpr(sykmelder.hprNummer),
            navn = finnNavn(sykmelder),
            land = null,
            idType = IdType.HPRNR,
        )
    }
}

fun finnNavn(sykmelder: Sykmelder): String {
    return "${sykmelder.fornavn} ${sykmelder.etternavn}"
}

data class Country(
    val id: Int,
    val alpha2: String,
    val alpha3: String,
    val name: String,
)

