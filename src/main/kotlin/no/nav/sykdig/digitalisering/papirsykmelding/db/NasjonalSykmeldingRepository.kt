package no.nav.sykdig.digitalisering.papirsykmelding.db

import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalSykmeldingDAO
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Transactional
@Repository
interface NasjonalSykmeldingRepository : JpaRepository<NasjonalSykmeldingDAO, String> {
    fun findBySykmeldingId(sykmeldingId: String): NasjonalSykmeldingDAO?
}
