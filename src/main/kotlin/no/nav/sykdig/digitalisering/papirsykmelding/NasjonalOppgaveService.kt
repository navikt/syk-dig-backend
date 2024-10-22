package no.nav.sykdig.digitalisering.papirsykmelding

import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalOppgaveRepository
import no.nav.sykdig.digitalisering.papirsykmelding.db.TestRepository
import org.springframework.stereotype.Service

@Service
class NasjonalOppgaveService(
    private val repository: NasjonalOppgaveRepository,
    private val trepo: TestRepository,
) {
    fun lagreOppgave(papirManuellOppgave: PapirManuellOppgave) {
        repository.lagreOppgave(papirManuellOppgave)
        TODO("Not yet implemented")
    }

    fun saveOppgave(papirManuellOppgave: PapirManuellOppgave): PapirManuellOppgave {
        trepo.save(papirManuellOppgave)
    }


}