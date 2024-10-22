package no.nav.sykdig.digitalisering.papirsykmelding.db

import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import org.springframework.data.repository.CrudRepository
import java.util.UUID


interface TestRepository :CrudRepository<PapirManuellOppgave, UUID>{

    fun findByAktorId(aktorId: String): List<PapirManuellOppgave>
}