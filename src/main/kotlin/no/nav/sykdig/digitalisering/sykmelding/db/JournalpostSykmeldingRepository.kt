package no.nav.sykdig.digitalisering.sykmelding.db

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime

@Repository
class JournalpostSykmeldingRepository(private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate) {
    fun insertJournalpostId(journalpostId: String): Int {
        val sql = """
        INSERT INTO journalpost_sykmelding (journalpost_id, created) 
        VALUES (:journalpost_id, NOW())
    """
        val params = mapOf("journalpost_id" to journalpostId)
        return namedParameterJdbcTemplate.update(sql, params)
    }

    fun getJournalpostSykmelding(journalpostId: String): JournalpostSykmelding? {
        val sql = """
        SELECT journalpost_id, created
        FROM journalpost_sykmelding 
        WHERE journalpost_id = :journalpost_id
    """
        val params = mapOf("journalpost_id" to journalpostId)
        return namedParameterJdbcTemplate.query(sql, params) { resultSet: ResultSet, _: Int ->
            JournalpostSykmelding(
                journalpostId = resultSet.getString("journalpost_id"),
                created = resultSet.getObject("created", OffsetDateTime::class.java),
            )
        }.firstOrNull()
    }
}
