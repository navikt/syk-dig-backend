package no.nav.sykdig.digitalisering.papirsykmelding.db

import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalSykmeldingDAO
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional
@Repository
interface NasjonalSykmeldingRepository : CrudRepository<NasjonalSykmeldingDAO, UUID> {
    fun findBySykmeldingId(sykmeldingId: String): NasjonalSykmeldingDAO?
}
