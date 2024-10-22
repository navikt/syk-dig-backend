package no.nav.sykdig.digitalisering.papirsykmelding.db

import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Transactional
@Repository
class NasjonalOppgaveRepository(private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate) {
    fun lagreOppgave(papirManuellOppgave: PapirManuellOppgave){
        namedParameterJdbcTemplate.update(
            """
                INSERT INTO nasjonal_manuelloppgave(sykmelding_id, journalpost_id, fnr, aktor_id, dokument_info_id, dato_opprettet, oppgave_id, papir_sm_registrering, utfall)
                VALUES (:sykmelding_id, :journalpost_id, :fnr, :aktor_id, :dokument_info_id, :dato_opprettet, :oppgave_id, papir_sm_registrering, :utfall)
            """.trimIndent()
        )
    }
}