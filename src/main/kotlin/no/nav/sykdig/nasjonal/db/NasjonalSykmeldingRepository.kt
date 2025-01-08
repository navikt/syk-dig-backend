package no.nav.sykdig.nasjonal.db

import no.nav.sykdig.nasjonal.db.model.NasjonalSykmeldingDAO
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional
@Repository
interface NasjonalSykmeldingRepository : CrudRepository<NasjonalSykmeldingDAO, UUID> {
    fun findBySykmeldingId(sykmeldingId: String): Optional<NasjonalSykmeldingDAO>
}
