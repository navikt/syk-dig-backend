package no.nav.sykdig.digitalisering.papirsykmelding.db

import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Transactional
@Repository
interface NasjonalOppgaveRepository : JpaRepository<NasjonalManuellOppgaveDAO, String> {
    fun findBySykmeldingId(sykmeldingId: String): NasjonalManuellOppgaveDAO?

//    fun updateBySykmeldingId(sykmeldingId: String): NasjonalManuellOppgaveDAO?
}
