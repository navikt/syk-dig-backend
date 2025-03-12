package no.nav.sykdig.nasjonal.services

import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.service.toSykmelding
import no.nav.sykdig.shared.LoggingMeta
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.Sykmelding
import no.nav.sykdig.utenlandsk.mapping.extractHelseOpplysningerArbeidsuforhet
import no.nav.sykdig.utenlandsk.mapping.fellesformatMarshaller
import no.nav.sykdig.utenlandsk.mapping.get
import no.nav.sykdig.utenlandsk.mapping.toString
import no.nav.sykdig.nasjonal.helsenett.SykmelderService
import no.nav.sykdig.nasjonal.models.Godkjenning
import no.nav.sykdig.nasjonal.models.SmRegistreringManuell
import no.nav.sykdig.nasjonal.models.Sykmelder
import no.nav.sykdig.nasjonal.models.Veileder
import no.nav.sykdig.nasjonal.db.models.NasjonalManuellOppgaveDAO
import no.nav.sykdig.pdl.PersonService
import no.nav.sykdig.utenlandsk.models.Merknad
import no.nav.sykdig.shared.ReceivedSykmelding
import no.nav.sykdig.shared.utils.getLocalDateTime
import no.nav.sykdig.shared.utils.mapsmRegistreringManuelltTilFellesformat
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

@Service
class NasjonalCommonService(
    private val sykmelderService: SykmelderService,
    private val personService: PersonService,
) {

    val log = applog()

    suspend fun createReceivedSykmelding(sykmeldingId: String, oppgave: NasjonalManuellOppgaveDAO, loggingMeta: LoggingMeta, smRegistreringManuell: SmRegistreringManuell, callId: String, sykmelder: Sykmelder): ReceivedSykmelding {
        log.info("Henter pasient fra PDL {} ", loggingMeta)
        val pasient =
            personService.getPerson(
                id = smRegistreringManuell.pasientFnr,
                callId = callId,
            )

        val tssId = sykmelderService.getTssIdInfotrygd(sykmelder.fnr!!, "", loggingMeta, sykmeldingId)

        val datoOpprettet = oppgave.datoOpprettet
        val journalpostId = oppgave.journalpostId
        val fellesformat =
            mapsmRegistreringManuelltTilFellesformat(
                smRegistreringManuell = smRegistreringManuell,
                pdlPasient = pasient,
                sykmelder = sykmelder,
                sykmeldingId = sykmeldingId,
                datoOpprettet = datoOpprettet?.toLocalDateTime(),
                journalpostId = journalpostId,
            )

        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
        val msgHead = fellesformat.get<XMLMsgHead>()

        val sykmelding =
            healthInformation.toSykmelding(
                sykmeldingId,
                pasient.aktorId,
                sykmelder.aktorId!!,
                sykmeldingId,
                getLocalDateTime(msgHead.msgInfo.genDate),
            )

        return ReceivedSykmelding(
            sykmelding = sykmelding,
            personNrPasient = pasient.fnr,
            tlfPasient =
                healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
            personNrLege = sykmelder.fnr,
            navLogId = sykmeldingId,
            msgId = sykmeldingId,
            legekontorOrgNr = null,
            legekontorOrgName = "",
            legekontorHerId = null,
            legekontorReshId = null,
            mottattDato = oppgave.datoOpprettet?.toLocalDateTime() ?: getLocalDateTime(msgHead.msgInfo.genDate),
            rulesetVersion = healthInformation.regelSettVersjon,
            fellesformat = fellesformatMarshaller.toString(fellesformat),
            tssid = tssId ?: "",
            merknader = createMerknad(sykmelding),
            partnerreferanse = null,
            legeHelsepersonellkategori =
                sykmelder.godkjenninger?.getHelsepersonellKategori(),
            legeHprNr = sykmelder.hprNummer,
            vedlegg = null,
            utenlandskSykmelding = null,
        )

    }

    private fun createMerknad(sykmelding: Sykmelding): List<Merknad>? {
        val behandletTidspunkt = sykmelding.behandletTidspunkt.toLocalDate()
        val terskel = sykmelding.perioder.map { it.fom }.minOrNull()?.plusDays(7)
        return if (behandletTidspunkt != null && terskel != null && behandletTidspunkt > terskel) {
            listOf(Merknad("TILBAKEDATERT_PAPIRSYKMELDING", null))
        } else {
            null
        }
    }

    private fun List<Godkjenning>.getHelsepersonellKategori(): String? =
        when {
            find { it.helsepersonellkategori?.verdi == "LE" } != null -> "LE"
            find { it.helsepersonellkategori?.verdi == "TL" } != null -> "TL"
            find { it.helsepersonellkategori?.verdi == "MT" } != null -> "MT"
            find { it.helsepersonellkategori?.verdi == "FT" } != null -> "FT"
            find { it.helsepersonellkategori?.verdi == "KI" } != null -> "KI"
            else -> {
                val verdi = firstOrNull()?.helsepersonellkategori?.verdi
                log.warn(
                    "Signerende behandler har ikke en helsepersonellkategori($verdi) vi kjenner igjen",
                )
                verdi
            }
        }

    fun getNavIdent(): Veileder {
        val authentication = SecurityContextHolder.getContext().authentication as JwtAuthenticationToken
        return Veileder(authentication.token.claims["NAVident"].toString())
    }

    fun getNavEmail(): String {
        val authentication = SecurityContextHolder.getContext().authentication as JwtAuthenticationToken
        return authentication.token.claims["preferred_username"].toString()
    }
}

fun isValidOppgaveId(oppgaveId: String): Boolean {
    val regex = Regex("^\\d{9}$|^[a-zA-Z0-9]{1,20}$")
    return oppgaveId.matches(regex)
}
