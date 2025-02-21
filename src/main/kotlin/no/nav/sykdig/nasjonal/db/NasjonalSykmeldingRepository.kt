package no.nav.sykdig.nasjonal.db

import no.nav.sykdig.nasjonal.db.models.NasjonalSykmeldingDAO
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Transactional
@Repository
interface NasjonalSykmeldingRepository : CrudRepository<NasjonalSykmeldingDAO, UUID> {
    fun findBySykmeldingId(sykmeldingId: String): List<NasjonalSykmeldingDAO>

    @Modifying
    @Query("DELETE FROM nasjonal_sykmelding n WHERE n.sykmelding_id = :sykmeldingId")
    fun deleteBySykmeldingId(sykmeldingId: String): Int
}
