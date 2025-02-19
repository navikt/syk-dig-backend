package no.nav.sykdig.nasjonal.services

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.sykdig.digitalisering.papirsykmelding.mapToDaoOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.mapToDaoSykmelding
import no.nav.sykdig.digitalisering.papirsykmelding.mapToUpdatedPapirSmRegistrering
import no.nav.sykdig.nasjonal.db.NasjonalOppgaveRepository
import no.nav.sykdig.nasjonal.db.NasjonalSykmeldingRepository
import no.nav.sykdig.nasjonal.db.models.NasjonalManuellOppgaveDAO
import no.nav.sykdig.nasjonal.mapping.isValidOppgaveId
import no.nav.sykdig.nasjonal.models.PapirManuellOppgave
import no.nav.sykdig.nasjonal.models.SmRegistreringManuell
import no.nav.sykdig.nasjonal.models.Veileder
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.securelog
import no.nav.sykdig.utenlandsk.models.ReceivedSykmelding
import org.springframework.stereotype.Service
import java.time.LocalDateTime


@Service
class NasjonalDbService(
    private val nasjonalOppgaveRepository: NasjonalOppgaveRepository,
    private val nasjonalSykmeldingRepository: NasjonalSykmeldingRepository,
) {

    val log = applog()
    val securelog = securelog()
    val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())


    fun saveOppgave(
        papirManuellOppgave: PapirManuellOppgave,
        ferdigstilt: Boolean = false,
    ): NasjonalManuellOppgaveDAO {
        val eksisterendeOppgave = nasjonalOppgaveRepository.findBySykmeldingId(papirManuellOppgave.sykmeldingId)
        securelog.info("Henter oppgave med sykmeldingId=${papirManuellOppgave.sykmeldingId}. Funnet: $eksisterendeOppgave")
        return if (eksisterendeOppgave != null) {
            log.info("Oppdaterer oppgave med sykmeldingId=${papirManuellOppgave.sykmeldingId}, database-id=${eksisterendeOppgave.id}")
            nasjonalOppgaveRepository.save(
                mapToDaoOppgave(
                    papirManuellOppgave,
                    eksisterendeOppgave.id,
                    ferdigstilt,
                ),
            )
        } else {
            val nyOppgave = nasjonalOppgaveRepository.save(
                mapToDaoOppgave(
                    papirManuellOppgave,
                    null,
                    ferdigstilt,
                ),
            )
            log.info("Lagret ny oppgave med sykmeldingId=${nyOppgave.sykmeldingId}, database-id=${nyOppgave.id}")
            securelog.info("Detaljer om lagret oppgave: $nyOppgave")

            nyOppgave
        }
    }

    fun saveSykmelding(receivedSykmelding: ReceivedSykmelding, veileder: Veileder) {
        val dao = mapToDaoSykmelding(receivedSykmelding, veileder)
        nasjonalSykmeldingRepository.save(dao)
    }

    fun updateOppgave(sykmeldingId: String, utfall: String, ferdigstiltAv: String, avvisningsgrunn: String?, smRegistreringManuell: SmRegistreringManuell?): NasjonalManuellOppgaveDAO? {
        val existingOppgave = nasjonalOppgaveRepository.findBySykmeldingId(sykmeldingId)

        if (existingOppgave == null) {
            log.info("Sykmelding $sykmeldingId not found")
            return null
        }

        val updatedOppgave = existingOppgave.copy(
            utfall = utfall,
            ferdigstiltAv = ferdigstiltAv,
            avvisningsgrunn = avvisningsgrunn,
            datoFerdigstilt = LocalDateTime.now(),
            ferdigstilt = true,
            papirSmRegistrering = mapToUpdatedPapirSmRegistrering(existingOppgave, smRegistreringManuell),
        )

        securelog.info("Lagret oppgave med sykmeldingId ${updatedOppgave.sykmeldingId} og med database id ${updatedOppgave.id} som dette objektet: $updatedOppgave")
        return nasjonalOppgaveRepository.save(updatedOppgave)
    }


    fun getOppgaveByOppgaveId(oppgaveId: String): NasjonalManuellOppgaveDAO? {
        if (!isValidOppgaveId(oppgaveId))
            throw IllegalArgumentException("Invalid oppgaveId does not contain only alphanumerical characters. oppgaveId: $oppgaveId")
        log.info("papirsykmelding: henter sykmelding med oppgaveId $oppgaveId fra nasjonal_manuelloppgave tabell")
        val oppgave = nasjonalOppgaveRepository.findByOppgaveId(oppgaveId.toInt()) ?: return null
        log.info("Hentet oppgave med oppgaveId $oppgaveId")
        securelog.info("hentet nasjonalOppgave fra db $oppgave")
        return oppgave
    }

    fun getOppgaveBySykmeldingId(sykmeldingId: String): NasjonalManuellOppgaveDAO? {
        log.info("papirsykmelding: henter sykmelding med sykmeldingId $sykmeldingId fra nasjonal_manuelloppgave tabell")
        val oppgave = nasjonalOppgaveRepository.findBySykmeldingId(sykmeldingId) ?: return null
        log.info("Hentet oppgave med sykmeldingId $sykmeldingId")
        securelog.info("hentet nasjonalOppgave fra db $oppgave")
        return oppgave
    }


    fun deleteOppgave(sykmeldingId: String): Int {
        return nasjonalOppgaveRepository.deleteBySykmeldingId(sykmeldingId)
    }

    fun deleteSykmelding(sykmeldingId: String): Int {
        return nasjonalSykmeldingRepository.deleteBySykmeldingId(sykmeldingId)
    }

}