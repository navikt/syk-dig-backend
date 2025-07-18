package no.nav.sykdig.dokarkiv

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.sykdig.shared.LoggingMeta
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.exceptions.IkkeTilgangException
import no.nav.sykdig.shared.Periode
import no.nav.sykdig.nasjonal.models.Sykmelder
import no.nav.sykdig.dokarkiv.model.*
import no.nav.sykdig.saf.graphql.AvsenderMottaker
import no.nav.sykdig.saf.graphql.AvsenderMottakerIdType
import no.nav.sykdig.saf.graphql.Journalposttype
import no.nav.sykdig.saf.graphql.SafQueryJournalpost
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
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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

    fun oppdaterOgFerdigstillUtenlandskJournalpost(
        landAlpha3: String?,
        fnr: String,
        enhet: String,
        dokumentinfoId: String?,
        journalpostId: String,
        sykmeldingId: String,
        perioder: List<Periode>?,
        source: String,
        avvisningsGrunn: String?,
        journalPost: SafQueryJournalpost,
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
            journalPost = journalPost,
            sykmeldtNavn = sykmeldtNavn,
        )
        ferdigstillJournalpost(
            enhet = enhet,
            journalpostId = journalpostId,
            sykmeldingId = sykmeldingId,
        )
    }

    @Retryable
    private fun oppdaterUtenlandskJournalpost(
        landAlpha3: String?,
        fnr: String,
        dokumentinfoId: String?,
        journalpostId: String,
        sykmeldingId: String,
        perioder: List<Periode>?,
        source: String,
        avvisningsGrunn: String?,
        journalPost: SafQueryJournalpost,
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
                journalPost,
                sykmeldtNavn,
            )
        oppdaterJournalpostRequest(body, sykmeldingId, journalpostId)
    }

    @Retryable
    private fun oppdaterJournalpostRequest(
        oppdaterJournalpostRequest: OppdaterJournalpostRequest,
        sykmeldingId: String,
        journalpostId: String,
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers["Nav-Callid"] = sykmeldingId

        try {
            securelog.info("createOppdaterJournalpostRequest: ${objectMapper.writeValueAsString(oppdaterJournalpostRequest)}")
            val response = dokarkivRestTemplate.exchange(
                "$url/$journalpostId",
                HttpMethod.PUT,
                HttpEntity(oppdaterJournalpostRequest, headers),
                String::class.java,
            )
            log.info("Oppdatert journalpost $journalpostId for sykmelding $sykmeldingId")
            return response
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder har ikke tilgang til å oppdatere journalpostId $journalpostId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til journalpost")
            } else if (e.statusCode.value() == 400) {
                log.error(
                    "HttpClientErrorException med responskode ${e.statusCode.value()} fra Dokarkiv ved oppdatering: ${e.message}",
                    e,
                )
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

    private fun createOppdaterUtenlandskJournalpostRequest(
        landAlpha3: String?,
        fnr: String,
        dokumentinfoId: String?,
        perioder: List<Periode>?,
        source: String,
        avvisningsGrunn: String?,
        journalPost: SafQueryJournalpost,
        sykmeldtNavn: String?,
    ): OppdaterJournalpostRequest {
        when (source) {
            "rina" -> {
                return OppdaterJournalpostRequest(
                    avsenderMottaker =
                        avsenderMottakerRequest(journalPost, landAlpha3, source, sykmeldtNavn, fnr),
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
                    avsenderMottaker = avsenderMottakerRequest(journalPost, landAlpha3, source, sykmeldtNavn, fnr),
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
                        avsenderMottakerRequest(journalPost, landAlpha3, source, sykmeldtNavn, fnr),
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

    private fun avsenderMottakerRequest(
        journalPost: SafQueryJournalpost,
        landAlpha3: String?,
        source: String,
        sykmeldtNavn: String?,
        fnr: String,
    ): AvsenderMottakerRequest? {
        if(journalPost.journalpost?.kanal == "NAV_NO") {
            return null
        }
        return createAvsenderMottaker(
            orginalAvsenderMottaker = journalPost.journalpost?.avsenderMottaker ?: throw RuntimeException("AvsenderMottaker er null"),
            land =
                if (landAlpha3 != null) {
                    mapFromAlpha3Toalpha2(landAlpha3)
                } else {
                    null
                },
            source = source,
            sykmeldtNavn = sykmeldtNavn,
            sykmeldtFnr = fnr,
        )
    }

    fun createAvsenderMottaker(
        orginalAvsenderMottaker: AvsenderMottaker?,
        land: String?,
        source: String,
        sykmeldtNavn: String?,
        sykmeldtFnr: String,
    ): AvsenderMottakerRequest {
        return AvsenderMottakerRequest(
            navn = mapNavn(orginalAvsenderMottaker, source, sykmeldtNavn),
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
        source: String,
        sykmeldtNavn: String?,
    ): String? {
        return if (!orginalAvsenderMottaker?.navn.isNullOrBlank()) {
            orginalAvsenderMottaker.navn
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
    private fun  ferdigstillJournalpost(
        enhet: String,
        journalpostId: String,
        sykmeldingId: String,
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers["Nav-Callid"] = sykmeldingId

        val body =
            FerdigstillJournalpostRequest(
                journalfoerendeEnhet = enhet,
            )
        try {
            val response = dokarkivRestTemplate.exchange(
                "$url/$journalpostId/ferdigstill",
                HttpMethod.PATCH,
                HttpEntity(body, headers),
                String::class.java,
            )
            log.info("Ferdigstilt journalpost $journalpostId for sykmelding $sykmeldingId")
            return response
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder har ikke tilgang til å ferdigstille journalpostId $journalpostId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til journalpost")
            } else if (e.statusCode.value() == 400) {
                log.error(
                    "HttpClientErrorException med responskode ${e.statusCode.value()} fra Dokarkiv ved oppdatering: ${e.message}",
                    e,
                )
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

    fun oppdaterOgFerdigstillNasjonalJournalpost(
        journalpostId: String,
        dokumentInfoId: String? = null,
        pasientFnr: String,
        sykmeldingId: String,
        sykmelder: Sykmelder,
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
        val hprNummer = sykmelder.hprNummer?.trimEnd()
        return AvsenderMottakerRequest(
            id = padHpr(hprNummer) ?: " ",
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

