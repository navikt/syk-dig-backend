package no.nav.sykdig.nasjonal.db

import no.nav.sykdig.nasjonal.db.models.NasjonalManuellOppgaveDAO
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional
@Repository
interface NasjonalOppgaveRepository : CrudRepository<NasjonalManuellOppgaveDAO, UUID> {
    fun findBySykmeldingId(sykmeldingId: String): NasjonalManuellOppgaveDAO?
    fun findByOppgaveId(oppgaveId: Int): NasjonalManuellOppgaveDAO?

    @Modifying
    @Query("DELETE FROM nasjonal_manuelloppgave n WHERE n.sykmelding_id = :sykmeldingId")
    fun deleteBySykmeldingId(sykmeldingId: String): Int
}
