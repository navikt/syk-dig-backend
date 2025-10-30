package no.nav.sykdig.utenlandsk.db

import java.sql.ResultSet
import java.time.OffsetDateTime
import no.nav.sykdig.utenlandsk.models.JournalpostSykmelding
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JournalpostSykmeldingRepository(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate
) {
    fun insertJournalpostId(journalpostId: String): Int {
        val sql =
            """
        INSERT INTO journalpost_sykmelding (journalpost_id, created) 
        VALUES (:journalpost_id, NOW()) on conflict do nothing 
    """
        val params = mapOf("journalpost_id" to journalpostId)
        return namedParameterJdbcTemplate.update(sql, params)
    }

    fun getJournalpostSykmelding(journalpostId: String): JournalpostSykmelding? {
        val sql =
            """
        select * from journalpost_sykmelding where journalpost_id = :journalpost_id
    
            and (exists(select 1 from oppgave where journalpost_id = :journalpost_id and avvisings_grunn is null) or not exists(select 1 from oppgave where journalpost_id = :journalpost_id));
    """
        val params = mapOf("journalpost_id" to journalpostId)
        return namedParameterJdbcTemplate
            .query(sql, params) { resultSet: ResultSet, _: Int ->
                JournalpostSykmelding(
                    journalpostId = resultSet.getString("journalpost_id"),
                    created = resultSet.getObject("created", OffsetDateTime::class.java),
                )
            }
            .firstOrNull()
    }
}
