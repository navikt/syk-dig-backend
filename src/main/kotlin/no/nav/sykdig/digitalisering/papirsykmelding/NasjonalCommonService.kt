package no.nav.sykdig.digitalisering.papirsykmelding

import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.service.toSykmelding
import no.nav.sykdig.LoggingMeta
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.felles.AvsenderSystem
import no.nav.sykdig.digitalisering.felles.KontaktMedPasient
import no.nav.sykdig.digitalisering.felles.Sykmelding
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.extractHelseOpplysningerArbeidsuforhet
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.fellesformatMarshaller
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.get
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.toString
import no.nav.sykdig.digitalisering.helsenett.SykmelderService
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Godkjenning
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.SmRegistreringManuell
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Sykmelder
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Veileder
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.sykmelding.Merknad
import no.nav.sykdig.digitalisering.sykmelding.ReceivedSykmelding
import no.nav.sykdig.securelog
import no.nav.sykdig.utils.getLocalDateTime
import no.nav.sykdig.utils.mapsmRegistreringManuelltTilFellesformat
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class NasjonalCommonService(
    private val sykmelderService: SykmelderService,
    private val personService: PersonService,
) {

    val log = applog()
    val securelog = securelog()


    suspend fun createReceivedSykmelding(sykmeldingId: String, oppgave: NasjonalManuellOppgaveDAO, loggingMeta: LoggingMeta, smRegistreringManuell: SmRegistreringManuell, callId: String, sykmelder: Sykmelder): ReceivedSykmelding {
        log.info("Henter pasient fra PDL {} ", loggingMeta)
        val pasient =
            personService.getPerson(
                id = smRegistreringManuell.pasientFnr,
                callId = callId,
            )

        val tssId = sykmelderService.getTssIdInfotrygd(sykmelder.fnr, "", loggingMeta, sykmeldingId)

        val datoOpprettet = oppgave.datoOpprettet
        val journalpostId = oppgave.journalpostId
        val fellesformat =
            mapsmRegistreringManuelltTilFellesformat(
                smRegistreringManuell = smRegistreringManuell,
                pdlPasient = pasient,
                sykmelder = sykmelder,
                sykmeldingId = sykmeldingId,
                datoOpprettet = datoOpprettet,
                journalpostId = journalpostId,
            )

        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
        val msgHead = fellesformat.get<XMLMsgHead>()

        val sykmelding =
            healthInformation.toSykmelding(
                sykmeldingId,
                pasient.aktorId,
                sykmelder.aktorId,
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
            mottattDato = oppgave.datoOpprettet ?: getLocalDateTime(msgHead.msgInfo.genDate),
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
    private fun toSykmelding(sykmeldingId: String, oppgave: NasjonalManuellOppgaveDAO): Sykmelding {
        requireNotNull(oppgave.papirSmRegistrering.aktorId) { "PapirSmRegistrering.aktorId er null" }
        requireNotNull(oppgave.papirSmRegistrering.medisinskVurdering) { "PapirSmRegistrering.medisinskVurdering er null" }
        requireNotNull(oppgave.papirSmRegistrering.arbeidsgiver) { "PapirSmRegistrering.arbeidsgiver er null" }
        requireNotNull(oppgave.papirSmRegistrering.behandler) { "PapirSmRegistrering.behandler er null" }
        return Sykmelding(
            id = sykmeldingId,
            msgId = sykmeldingId,
            pasientAktoerId = oppgave.papirSmRegistrering.aktorId,
            medisinskVurdering = oppgave.papirSmRegistrering.medisinskVurdering,
            skjermesForPasient = oppgave.papirSmRegistrering.skjermesForPasient ?: false,
            arbeidsgiver = oppgave.papirSmRegistrering.arbeidsgiver,
            perioder = oppgave.papirSmRegistrering.perioder ?: emptyList(),
            prognose = oppgave.papirSmRegistrering.prognose,
            utdypendeOpplysninger = oppgave.papirSmRegistrering.utdypendeOpplysninger ?: emptyMap(),
            tiltakArbeidsplassen = oppgave.papirSmRegistrering.tiltakArbeidsplassen,
            tiltakNAV = oppgave.papirSmRegistrering.tiltakNAV,
            andreTiltak = oppgave.papirSmRegistrering.andreTiltak,
            meldingTilNAV = oppgave.papirSmRegistrering.meldingTilNAV,
            meldingTilArbeidsgiver = oppgave.papirSmRegistrering.meldingTilArbeidsgiver,
            kontaktMedPasient = KontaktMedPasient(
                kontaktDato = oppgave.papirSmRegistrering.kontaktMedPasient?.kontaktDato,
                begrunnelseIkkeKontakt = oppgave.papirSmRegistrering.kontaktMedPasient?.begrunnelseIkkeKontakt
            ),
            behandletTidspunkt = LocalDateTime.from(oppgave.papirSmRegistrering.behandletTidspunkt),
            behandler = oppgave.papirSmRegistrering.behandler,
            avsenderSystem = AvsenderSystem( //TODO
                navn = "Navn avsendersystem",
                versjon = "0.0"
            ),
            syketilfelleStartDato = oppgave.papirSmRegistrering.syketilfelleStartDato,
            signaturDato = LocalDateTime.from(oppgave.papirSmRegistrering.behandletTidspunkt),
            navnFastlege = "Fastlege navn", //TODO
        )
    }
}
