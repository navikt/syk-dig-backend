package no.nav.sykdig.digitalisering.papirsykmelding.db

import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional
@Repository
interface NasjonalOppgaveRepository : CrudRepository<NasjonalManuellOppgaveDAO, UUID> {
    fun findBySykmeldingId(sykmeldingId: String): NasjonalManuellOppgaveDAO?
    fun findByOppgaveId(oppgaveId: Int): NasjonalManuellOppgaveDAO?
}
