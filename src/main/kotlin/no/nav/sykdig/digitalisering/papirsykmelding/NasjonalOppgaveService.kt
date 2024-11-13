package no.nav.sykdig.digitalisering.papirsykmelding

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalOppgaveRepository
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import org.springframework.stereotype.Service
import java.time.ZoneOffset
import java.util.*

@Service
class NasjonalOppgaveService(
    private val nasjonalOppgaveRepository: NasjonalOppgaveRepository,
) {
    fun lagreOppgave(papirManuellOppgave: PapirManuellOppgave): NasjonalManuellOppgaveDAO {
        val eksisterendeOppgave = nasjonalOppgaveRepository.findBySykmeldingId(papirManuellOppgave.sykmeldingId)
        if (eksisterendeOppgave.isPresent) {
            return nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, eksisterendeOppgave.get().id))
        }
        return nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, null))
    }

    fun hentFerdigstiltOppgave(sykmeldingId: String): PapirManuellOppgave? {
        val eksisterendeOppgave = nasjonalOppgaveRepository.findBySykmeldingId(sykmeldingId)
        if (eksisterendeOppgave.isPresent && eksisterendeOppgave.get().ferdigstilt) {
            return mapFromDao(eksisterendeOppgave.get())
        }
        return null
    }

    fun mapToDao(
        papirManuellOppgave: PapirManuellOppgave,
        existingId: UUID?,
    ): NasjonalManuellOppgaveDAO {
        val mapper = jacksonObjectMapper()
        mapper.registerModules(JavaTimeModule())

        val nasjonalManuellOppgaveDAO =
            NasjonalManuellOppgaveDAO(
                sykmeldingId = papirManuellOppgave.sykmeldingId,
                journalpostId = papirManuellOppgave.papirSmRegistering.journalpostId,
                fnr = papirManuellOppgave.fnr,
                aktorId = papirManuellOppgave.papirSmRegistering.aktorId,
                dokumentInfoId = papirManuellOppgave.papirSmRegistering.dokumentInfoId,
                datoOpprettet = papirManuellOppgave.papirSmRegistering.datoOpprettet?.toLocalDateTime(),
                oppgaveId = papirManuellOppgave.oppgaveid,
                ferdigstilt = false,
                papirSmRegistrering =
                    PapirSmRegistering(
                        journalpostId = papirManuellOppgave.papirSmRegistering.journalpostId,
                        oppgaveId = papirManuellOppgave.papirSmRegistering.oppgaveId,
                        fnr = papirManuellOppgave.papirSmRegistering.fnr,
                        aktorId = papirManuellOppgave.papirSmRegistering.aktorId,
                        dokumentInfoId = papirManuellOppgave.papirSmRegistering.dokumentInfoId,
                        datoOpprettet = papirManuellOppgave.papirSmRegistering.datoOpprettet,
                        sykmeldingId = papirManuellOppgave.papirSmRegistering.sykmeldingId,
                        syketilfelleStartDato = papirManuellOppgave.papirSmRegistering.syketilfelleStartDato,
                        arbeidsgiver = papirManuellOppgave.papirSmRegistering.arbeidsgiver,
                        medisinskVurdering = papirManuellOppgave.papirSmRegistering.medisinskVurdering,
                        skjermesForPasient = papirManuellOppgave.papirSmRegistering.skjermesForPasient,
                        perioder = papirManuellOppgave.papirSmRegistering.perioder,
                        prognose = papirManuellOppgave.papirSmRegistering.prognose,
                        utdypendeOpplysninger = papirManuellOppgave.papirSmRegistering.utdypendeOpplysninger,
                        tiltakNAV = papirManuellOppgave.papirSmRegistering.tiltakNAV,
                        tiltakArbeidsplassen = papirManuellOppgave.papirSmRegistering.tiltakArbeidsplassen,
                        andreTiltak = papirManuellOppgave.papirSmRegistering.andreTiltak,
                        meldingTilNAV = papirManuellOppgave.papirSmRegistering.meldingTilNAV,
                        meldingTilArbeidsgiver = papirManuellOppgave.papirSmRegistering.meldingTilArbeidsgiver,
                        kontaktMedPasient = papirManuellOppgave.papirSmRegistering.kontaktMedPasient,
                        behandletTidspunkt = papirManuellOppgave.papirSmRegistering.behandletTidspunkt,
                        behandler = papirManuellOppgave.papirSmRegistering.behandler,
                    ),
                utfall = null,
                ferdigstiltAv = null,
                datoFerdigstilt = null,
                avvisningsgrunn = null,
            )

        if (existingId != null) {
            nasjonalManuellOppgaveDAO.apply {
                id = existingId
            }
        }

        return nasjonalManuellOppgaveDAO
    }

    fun mapFromDao(nasjonalManuellOppgaveDAO: NasjonalManuellOppgaveDAO): PapirManuellOppgave {
        return PapirManuellOppgave(
            sykmeldingId = nasjonalManuellOppgaveDAO.sykmeldingId,
            papirSmRegistering = PapirSmRegistering(
                journalpostId = nasjonalManuellOppgaveDAO.journalpostId,
                oppgaveId = nasjonalManuellOppgaveDAO.oppgaveId?.toString(),
                fnr = nasjonalManuellOppgaveDAO.fnr,
                aktorId = nasjonalManuellOppgaveDAO.aktorId,
                dokumentInfoId = nasjonalManuellOppgaveDAO.dokumentInfoId,
                datoOpprettet = nasjonalManuellOppgaveDAO.datoOpprettet?.atOffset(ZoneOffset.UTC),
                sykmeldingId = nasjonalManuellOppgaveDAO.papirSmRegistrering.sykmeldingId,
                syketilfelleStartDato = nasjonalManuellOppgaveDAO.papirSmRegistrering.syketilfelleStartDato,
                arbeidsgiver = nasjonalManuellOppgaveDAO.papirSmRegistrering.arbeidsgiver,
                medisinskVurdering = nasjonalManuellOppgaveDAO.papirSmRegistrering.medisinskVurdering,
                skjermesForPasient = nasjonalManuellOppgaveDAO.papirSmRegistrering.skjermesForPasient,
                perioder = nasjonalManuellOppgaveDAO.papirSmRegistrering.perioder,
                prognose = nasjonalManuellOppgaveDAO.papirSmRegistrering.prognose,
                utdypendeOpplysninger = nasjonalManuellOppgaveDAO.papirSmRegistrering.utdypendeOpplysninger,
                tiltakNAV = nasjonalManuellOppgaveDAO.papirSmRegistrering.tiltakNAV,
                tiltakArbeidsplassen = nasjonalManuellOppgaveDAO.papirSmRegistrering.tiltakArbeidsplassen,
                andreTiltak = nasjonalManuellOppgaveDAO.papirSmRegistrering.andreTiltak,
                meldingTilNAV = nasjonalManuellOppgaveDAO.papirSmRegistrering.meldingTilNAV,
                meldingTilArbeidsgiver = nasjonalManuellOppgaveDAO.papirSmRegistrering.meldingTilArbeidsgiver,
                kontaktMedPasient = nasjonalManuellOppgaveDAO.papirSmRegistrering.kontaktMedPasient,
                behandletTidspunkt = nasjonalManuellOppgaveDAO.papirSmRegistrering.behandletTidspunkt,
                behandler = nasjonalManuellOppgaveDAO.papirSmRegistrering.behandler,
            ),
            fnr = nasjonalManuellOppgaveDAO.fnr,
            oppgaveid = nasjonalManuellOppgaveDAO.oppgaveId!!,
            pdfPapirSykmelding = byteArrayOf(),
            documents = emptyList()
        )
    }

}
