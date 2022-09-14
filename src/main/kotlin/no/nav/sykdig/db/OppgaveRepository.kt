package no.nav.sykdig.db

import no.nav.sykdig.model.DigitaliseringsoppgaveDbModel
import no.nav.sykdig.model.SykmeldingUnderArbeid
import no.nav.sykdig.objectMapper
import org.postgresql.util.PGobject
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZoneOffset
import java.util.UUID

@Transactional
@Repository
class OppgaveRepository(private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate) {
    fun lagreOppgave(digitaliseringsoppgave: DigitaliseringsoppgaveDbModel) {
        namedParameterJdbcTemplate.update(
            """
            INSERT INTO oppgave(oppgave_id, fnr, journalpost_id, dokumentinfo_id, opprettet, ferdigstilt)
            VALUES (:oppgave_id, :fnr, :journalpost_id, :dokumentinfo_id, :opprettet, :ferdigstilt)
        """,
            MapSqlParameterSource()
                .addValue("oppgave_id", digitaliseringsoppgave.oppgaveId)
                .addValue("fnr", digitaliseringsoppgave.fnr)
                .addValue("journalpost_id", digitaliseringsoppgave.journalpostId)
                .addValue("dokumentinfo_id", digitaliseringsoppgave.dokumentInfoId)
                .addValue("opprettet", Timestamp.from(digitaliseringsoppgave.opprettet.toInstant()))
                .addValue("ferdigstilt", digitaliseringsoppgave.ferdigstilt?.let { Timestamp.from(digitaliseringsoppgave.ferdigstilt.toInstant()) })
        )
        namedParameterJdbcTemplate.update(
            """
            INSERT INTO sykmelding(sykmelding_id, oppgave_id, type, sykmelding, endret_av, timestamp)
            VALUES (:sykmelding_id, :oppgave_id, :type, :sykmelding, :endret_av, :timestamp)
        """,
            mapOf(
                "sykmelding_id" to digitaliseringsoppgave.sykmeldingId,
                "oppgave_id" to digitaliseringsoppgave.oppgaveId,
                "type" to digitaliseringsoppgave.type,
                "sykmelding" to digitaliseringsoppgave.sykmelding?.toPGObject(),
                "endret_av" to digitaliseringsoppgave.endretAv,
                "timestamp" to Timestamp.from(digitaliseringsoppgave.timestamp.toInstant()),
            )
        )
    }

    fun getOppgave(oppgaveId: String): DigitaliseringsoppgaveDbModel? {
        return namedParameterJdbcTemplate.query(
            """
            SELECT o.oppgave_id,
                    fnr,
                    journalpost_id,
                    dokumentinfo_id,
                    opprettet,
                    ferdigstilt,
                    sykmelding_id,
                    type,
                    sykmelding,
                    endret_av,
                    timestamp
                    FROM oppgave AS o
                        LEFT OUTER JOIN sykmelding AS s ON o.oppgave_id = s.oppgave_id AND
                                                                   s.timestamp = (SELECT timestamp
                                                                                             FROM sykmelding
                                                                                             WHERE s.oppgave_id = o.oppgave_id
                                                                                             ORDER BY timestamp DESC
                                                                                             LIMIT 1)
                    where o.oppgave_id = :oppgave_id;
            """,
            mapOf("oppgave_id" to oppgaveId)
        ) { resultSet, _ ->
            resultSet.toDigitaliseringsoppgave()
        }.firstOrNull()
    }

    fun deleteAll() {
        namedParameterJdbcTemplate.update("DELETE FROM sykmelding", MapSqlParameterSource())
    }
}

fun SykmeldingUnderArbeid.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}

private fun ResultSet.toDigitaliseringsoppgave(): DigitaliseringsoppgaveDbModel =
    DigitaliseringsoppgaveDbModel(
        oppgaveId = getString("oppgave_id"),
        fnr = getString("fnr"),
        journalpostId = getString("journalpost_id"),
        dokumentInfoId = getString("dokumentinfo_id"),
        opprettet = getTimestamp("opprettet").toInstant().atOffset(ZoneOffset.UTC),
        ferdigstilt = getTimestamp("ferdigstilt")?.toInstant()?.atOffset(ZoneOffset.UTC),
        sykmeldingId = UUID.fromString(getString("sykmelding_id")),
        type = getString("type"),
        sykmelding = getString("sykmelding")?.let { objectMapper.readValue(it, SykmeldingUnderArbeid::class.java) },
        endretAv = getString("endret_av"),
        timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC)
    )
