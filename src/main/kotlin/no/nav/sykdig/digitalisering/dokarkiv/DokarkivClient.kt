package no.nav.sykdig.digitalisering.dokarkiv

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
import no.nav.sykdig.digitalisering.saf.graphql.AvsenderMottaker
import no.nav.sykdig.digitalisering.saf.graphql.AvsenderMottakerIdType
import no.nav.sykdig.digitalisering.sykmelding.Periode
import no.nav.sykdig.objectMapper
import no.nav.sykdig.securelog
import no.nav.sykdig.utils.createTitle
import no.nav.sykdig.utils.createTitleNavNo
import no.nav.sykdig.utils.createTitleRina
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
import java.util.Locale

@Component
class DokarkivClient(
    @Value("\${dokarkiv.url}") private val url: String,
    private val dokarkivRestTemplate: RestTemplate,
) {
    val log = applog()
    val securelog = securelog()

    fun updateDocument(
        journalpostid: String,
        documentId: String,
        tittel: String,
    ) {
        val oppaterDokumentRequest =
            OppdaterDokumentRequest(
                dokumenter =
                    listOf(
                        DokumentInfo(
                            dokumentInfoId = documentId,
                            tittel = tittel,
                        ),
                    ),
            )
        dokarkivRestTemplate.put(
            "$url/$journalpostid",
            oppaterDokumentRequest,
        )
    }

    fun oppdaterOgFerdigstillJournalpost(
        landAlpha3: String?,
        fnr: String,
        enhet: String,
        dokumentinfoId: String,
        journalpostId: String,
        sykmeldingId: String,
        perioder: List<Periode>?,
        source: String,
        avvisningsGrunn: String?,
        orginalAvsenderMottaker: AvsenderMottaker?,
        sykmeldtNavn: String?,
    ) {
        oppdaterJournalpost(
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
    private fun oppdaterJournalpost(
        landAlpha3: String?,
        fnr: String,
        dokumentinfoId: String,
        journalpostId: String,
        sykmeldingId: String,
        perioder: List<Periode>?,
        source: String,
        avvisningsGrunn: String?,
        orginalAvsenderMottaker: AvsenderMottaker?,
        sykmeldtNavn: String?,
    ) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers["Nav-Callid"] = sykmeldingId

        val body =
            createOppdaterJournalpostRequest(
                landAlpha3,
                fnr,
                dokumentinfoId,
                perioder,
                source,
                avvisningsGrunn,
                orginalAvsenderMottaker,
                sykmeldtNavn,
            )

        try {
            securelog.info("createOppdaterJournalpostRequest: ${objectMapper.writeValueAsString(body)}")
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
            } else if (e.statusCode.value() == 400) {
                log.error(
                    "HttpClientErrorException med responskode ${e.statusCode.value()} fra Dokarkiv ved oppdatering: ${e.message}",
                    e,
                )
                if (journalpostId == "676155741") {
                    log.warn("Skipping update of journalpost $journalpostId")
                } else {
                    throw e
                }
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

    private fun createOppdaterJournalpostRequest(
        landAlpha3: String?,
        fnr: String,
        dokumentinfoId: String,
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
    private fun ferdigstillJournalpost(
        enhet: String,
        journalpostId: String,
        sykmeldingId: String,
    ) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers["Nav-Callid"] = sykmeldingId

        val body =
            FerdigstillJournalpostRequest(
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
            } else if (e.statusCode.value() == 400) {
                log.error(
                    "HttpClientErrorException med responskode ${e.statusCode.value()} fra Dokarkiv ved oppdatering: ${e.message}",
                    e,
                )
                if (journalpostId == "676155741") {
                    log.warn("Skipping update of journalpost $journalpostId")
                } else {
                    throw e
                }
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
}

data class Country(
    val id: Int,
    val alpha2: String,
    val alpha3: String,
    val name: String,
)
