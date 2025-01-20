package no.nav.sykdig.nasjonal.db

import no.nav.sykdig.nasjonal.db.models.NasjonalManuellOppgaveDAO
import no.nav.sykdig.nasjonal.db.models.NasjonalSykmeldingDAO
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional
@Repository
interface NasjonalOppgaveRepository : CrudRepository<NasjonalManuellOppgaveDAO, UUID> {
    fun findBySykmeldingId(sykmeldingId: String): NasjonalManuellOppgaveDAO?
    fun findByOppgaveId(oppgaveId: Int): NasjonalManuellOppgaveDAO?
    fun deleteBySykmeldingId(sykmeldingId: String): NasjonalManuellOppgaveDAO?
}
