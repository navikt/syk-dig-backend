package no.nav.sykdig.digitalisering.papirsykmelding.db

import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Transactional
@Repository
interface NasjonalOppgaveRepository : CrudRepository<NasjonalManuellOppgaveDAO, String> {
    fun findBySykmeldingId(sykmeldingId: String): NasjonalManuellOppgaveDAO?
}
