package no.nav.sykdig.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.model.RegisterOppgaveValues
import no.nav.sykdig.digitalisering.felles.AktivitetIkkeMulig
import no.nav.sykdig.digitalisering.felles.Diagnose
import no.nav.sykdig.digitalisering.felles.Gradert
import no.nav.sykdig.digitalisering.felles.MedisinskVurdering
import no.nav.sykdig.digitalisering.felles.Periode
import no.nav.sykdig.digitalisering.sykmelding.UtenlandskSykmelding
import no.nav.sykdig.generated.types.Avvisingsgrunn
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.PeriodeType
import no.nav.sykdig.model.DokumentDbModel
import no.nav.sykdig.model.OppgaveDbModel
import no.nav.sykdig.model.SDSykmelding
import no.nav.sykdig.model.SykmeldingUnderArbeid
import no.nav.sykdig.objectMapper
import no.nav.sykdig.utils.getDiagnoseText
import org.postgresql.util.PGobject
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Transactional
@Repository
class OppgaveRepository(private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate) {
    fun lagreOppgave(digitaliseringsoppgave: OppgaveDbModel) {
        namedParameterJdbcTemplate.update(
            """
            INSERT INTO oppgave(oppgave_id, fnr, journalpost_id, dokumentinfo_id, opprettet, ferdigstilt, dokumenter, source)
            VALUES (:oppgave_id, :fnr, :journalpost_id, :dokumentinfo_id, :opprettet, :ferdigstilt, :dokumenter, :source) on conflict(oppgave_id) do nothing ;
        """,
            MapSqlParameterSource()
                .addValue("oppgave_id", digitaliseringsoppgave.oppgaveId)
                .addValue("fnr", digitaliseringsoppgave.fnr)
                .addValue("journalpost_id", digitaliseringsoppgave.journalpostId)
                .addValue("dokumentinfo_id", digitaliseringsoppgave.dokumentInfoId)
                .addValue("dokumenter", digitaliseringsoppgave.dokumenter.toPGObject())
                .addValue("opprettet", Timestamp.from(digitaliseringsoppgave.opprettet.toInstant()))
                .addValue(
                    "ferdigstilt",
                    digitaliseringsoppgave.ferdigstilt?.let { Timestamp.from(digitaliseringsoppgave.ferdigstilt.toInstant()) },
                )
                .addValue("source", digitaliseringsoppgave.source),
        )
        namedParameterJdbcTemplate.update(
            """
            INSERT INTO sykmelding(sykmelding_id, oppgave_id, type, sykmelding, endret_av, timestamp)
            VALUES (:sykmelding_id, :oppgave_id, :type, :sykmelding, :endret_av, :timestamp) on conflict (sykmelding_id, timestamp) do nothing;
        """,
            mapOf(
                "sykmelding_id" to digitaliseringsoppgave.sykmeldingId,
                "oppgave_id" to digitaliseringsoppgave.oppgaveId,
                "type" to digitaliseringsoppgave.type,
                "sykmelding" to digitaliseringsoppgave.sykmelding?.toPGObject(),
                "endret_av" to digitaliseringsoppgave.endretAv,
                "timestamp" to Timestamp.from(digitaliseringsoppgave.timestamp.toInstant()),
            ),
        )
    }
    fun getOppgaveBySykmeldingId(sykmeldingId: String): OppgaveDbModel? {
        return namedParameterJdbcTemplate.query(
            """SELECT o.oppgave_id,
               o.fnr,
               o.journalpost_id,
               o.dokumentinfo_id,
               o.dokumenter,
               o.opprettet,
               o.ferdigstilt,
               o.avvisings_grunn,
               o.tilbake_til_gosys,
               s.sykmelding_id,
               s.type,
               s.sykmelding,
               s.endret_av,
               s.timestamp,
               o.source
        FROM sykmelding s
                 INNER JOIN oppgave o ON o.oppgave_id = s.oppgave_id
        WHERE s.timestamp = (SELECT MAX(timestamp)
                             FROM sykmelding
                             WHERE sykmelding_id = s.sykmelding_id)
        and  s.sykmelding_id = :sykmelding_id;
            """,
            mapOf("sykmelding_id" to sykmeldingId),
        ) { resultSet, _ ->
            resultSet.toDigitaliseringsoppgave()
        }.firstOrNull()
    }

    fun getOppgave(oppgaveId: String): OppgaveDbModel? {
        return namedParameterJdbcTemplate.query(
            """SELECT o.oppgave_id,
                           fnr,
                           journalpost_id,
                           dokumentinfo_id,
                           dokumenter,
                           opprettet,
                           ferdigstilt,
                           avvisings_grunn,
                           tilbake_til_gosys,
                           sykmelding_id,
                           type,
                           s.sykmelding,
                           endret_av,
                           timestamp,
                           source
                    FROM oppgave AS o
                             INNER JOIN sykmelding AS s ON o.oppgave_id = s.oppgave_id
                        AND s.timestamp = (SELECT MAX(timestamp)
                                           FROM sykmelding
                                           WHERE oppgave_id = o.oppgave_id)
                    WHERE o.oppgave_id = :oppgave_id;
            """,
            mapOf("oppgave_id" to oppgaveId),
        ) { resultSet, _ ->
            resultSet.toDigitaliseringsoppgave()
        }.firstOrNull()
    }

    @Transactional
    fun updateOppgave(
        oppgave: OppgaveDbModel,
        sykmelding: SykmeldingUnderArbeid,
        navEpost: String,
        ferdigstilles: Boolean,
    ) {
        if (ferdigstilles) {
            namedParameterJdbcTemplate.update(
                """
                UPDATE oppgave
                SET ferdigstilt = :ferdigstilt
                WHERE oppgave_id = :oppgave_id
                """.trimIndent(),
                mapOf(
                    "oppgave_id" to oppgave.oppgaveId,
                    "ferdigstilt" to Timestamp.from(Instant.now()),
                ),
            )
        }
        updateSykmelding(oppgave, navEpost, sykmelding)
    }

    fun updateSykmelding(
        oppgave: OppgaveDbModel,
        navEpost: String,
        sykmelding: SykmeldingUnderArbeid,
    ) {
        namedParameterJdbcTemplate.update(
            """
                INSERT INTO sykmelding(sykmelding_id, oppgave_id, type, sykmelding, endret_av, timestamp)
                VALUES (:sykmelding_id, :oppgave_id, :type, :sykmelding, :endret_av, :timestamp)
                """.trimIndent(),
            mapOf(
                "sykmelding_id" to oppgave.sykmeldingId.toString(),
                "oppgave_id" to oppgave.oppgaveId,
                "type" to oppgave.type,
                "endret_av" to navEpost,
                "timestamp" to Timestamp.from(Instant.now()),
                "sykmelding" to sykmelding.toPGObject(),
            ),
        )
    }

    @Transactional
    fun ferdigstillOppgaveGosys(
        oppgave: OppgaveDbModel,
        navEpost: String,
        sykmelding: SykmeldingUnderArbeid?,
    ) {
        namedParameterJdbcTemplate.update(
            """
            INSERT INTO sykmelding(sykmelding_id, oppgave_id, type, sykmelding, endret_av, timestamp)
            VALUES (:sykmelding_id, :oppgave_id, :type, :sykmelding, :endret_av, :timestamp)
            """.trimIndent(),
            mapOf(
                "sykmelding_id" to oppgave.sykmeldingId.toString(),
                "oppgave_id" to oppgave.oppgaveId,
                "type" to oppgave.type,
                "endret_av" to navEpost,
                "timestamp" to Timestamp.from(Instant.now()),
                "sykmelding" to sykmelding?.toPGObject(),
            ),
        )
        namedParameterJdbcTemplate.update(
            """
            UPDATE oppgave
            SET ferdigstilt = :ferdigstilt,
                tilbake_til_gosys = :tilbake_til_gosys
            WHERE oppgave_id = :oppgave_id
            """.trimIndent(),
            mapOf(
                "oppgave_id" to oppgave.oppgaveId,
                "ferdigstilt" to Timestamp.from(Instant.now()),
                "tilbake_til_gosys" to true,
            ),
        )
    }

    @Transactional
    fun ferdigstillAvvistOppgave(
        oppgave: OppgaveDbModel,
        navEpost: String,
        sykmelding: SykmeldingUnderArbeid?,
        avvisningsgrunn: Avvisingsgrunn,
    ) {
        namedParameterJdbcTemplate.update(
            """
            INSERT INTO sykmelding(sykmelding_id, oppgave_id, type, sykmelding, endret_av, timestamp)
            VALUES (:sykmelding_id, :oppgave_id, :type, :sykmelding, :endret_av, :timestamp)
            """.trimIndent(),
            mapOf(
                "sykmelding_id" to oppgave.sykmeldingId.toString(),
                "oppgave_id" to oppgave.oppgaveId,
                "type" to oppgave.type,
                "endret_av" to navEpost,
                "timestamp" to Timestamp.from(Instant.now()),
                "sykmelding" to sykmelding?.toPGObject(),
            ),
        )
        namedParameterJdbcTemplate.update(
            """
            UPDATE oppgave
            SET ferdigstilt = :ferdigstilt,
                avvisings_grunn = :avvisings_grunn
            WHERE oppgave_id = :oppgave_id
            """.trimIndent(),
            mapOf(
                "oppgave_id" to oppgave.oppgaveId,
                "ferdigstilt" to Timestamp.from(Instant.now()),
                "avvisings_grunn" to avvisningsgrunn.name,
            ),
        )
    }

    @Transactional
    fun getLastSykmelding(oppgaveId: String): SykmeldingUnderArbeid? {
        return namedParameterJdbcTemplate.query(
            """SELECT s.sykmelding
                    FROM sykmelding AS s
                             INNER JOIN oppgave AS o ON s.oppgave_id = o.oppgave_id
                        AND s.timestamp = (SELECT MAX(timestamp)
                                           FROM sykmelding
                                           WHERE oppgave_id = o.oppgave_id)
                    WHERE s.oppgave_id = :oppgave_id;
            """,
            mapOf("oppgave_id" to oppgaveId),
        ) { resultSet, _ ->
            resultSet.toSykmeldingUnderArbeid()
        }.firstOrNull()
    }

    fun updateDocuments(
        oppgaveId: String,
        dokumenter: List<DokumentDbModel>,
    ) {
        namedParameterJdbcTemplate.update(
            """
            UPDATE oppgave
            SET dokumenter = :dokumenter
            WHERE oppgave_id = :oppgave_id
            """.trimIndent(),
            mapOf(
                "oppgave_id" to oppgaveId,
                "dokumenter" to dokumenter.toPGObject(),
            ),
        )
    }

}

fun toSykmelding(
    oppgave: OppgaveDbModel,
    values: RegisterOppgaveValues,
): SykmeldingUnderArbeid {
    val sykmeldingsId = oppgave.sykmeldingId.toString()
    if (oppgave.sykmelding == null) {
        val msgId = UUID.randomUUID().toString()
        return SykmeldingUnderArbeid(
            sykmelding =
                SDSykmelding(
                    id = sykmeldingsId,
                    msgId = msgId,
                    medisinskVurdering = values.mapToMedisinskVurdering(),
                    arbeidsgiver = null,
                    perioder = values.perioder.mapToPerioder(),
                    prognose = null,
                    utdypendeOpplysninger = null,
                    tiltakArbeidsplassen = null,
                    tiltakNAV = null,
                    andreTiltak = null,
                    meldingTilNAV = null,
                    meldingTilArbeidsgiver = null,
                    kontaktMedPasient = null,
                    behandletTidspunkt = values.behandletTidspunkt,
                    behandler = null,
                    syketilfelleStartDato = null,
                ),
            fnrPasient = values.fnrPasient,
            fnrLege = null,
            legeHprNr = null,
            navLogId = UUID.randomUUID().toString(),
            msgId = msgId,
            legekontorOrgNr = null,
            legekontorHerId = null,
            legekontorOrgName = null,
            mottattDato = null,
            utenlandskSykmelding = toUtenlandskSykmelding(values, sykmeldingsId),
        )
    } else {
        val sykmelding: SykmeldingUnderArbeid = oppgave.sykmelding
        sykmelding.sykmelding.medisinskVurdering = values.mapToMedisinskVurdering()
        sykmelding.sykmelding.perioder = values.perioder.mapToPerioder()
        sykmelding.sykmelding.behandletTidspunkt = values.behandletTidspunkt
        sykmelding.fnrPasient = values.fnrPasient
        sykmelding.utenlandskSykmelding = toUtenlandskSykmelding(values, sykmeldingsId)
        return sykmelding
    }
}

private fun toUtenlandskSykmelding(
    values: RegisterOppgaveValues,
    sykmeldingsId: String,
): UtenlandskSykmelding? {
    val log = applog(OppgaveRepository::class.java.toString())
    log.info(
        "folkeRegistertAdresseErBrakkeEllerTilsvarende: {}, og skrevetLand {}, og erAdresseUtland: {} \n med sykmeldingsId={}",
        values.folkeRegistertAdresseErBrakkeEllerTilsvarende,
        values.skrevetLand,
        values.erAdresseUtland,
        sykmeldingsId,
    )
    return values.skrevetLand?.let { skrevetLand ->
        UtenlandskSykmelding(
            land = skrevetLand,
            folkeRegistertAdresseErBrakkeEllerTilsvarende =
                values.folkeRegistertAdresseErBrakkeEllerTilsvarende
                    ?: false,
            erAdresseUtland = values.erAdresseUtland ?: false,
        )
    }
}

private fun List<PeriodeInput>?.mapToPerioder(): List<Periode>? =
    this?.map {
        Periode(
            fom = it.fom,
            tom = it.tom,
            aktivitetIkkeMulig =
                if (it.type == PeriodeType.AKTIVITET_IKKE_MULIG) {
                    AktivitetIkkeMulig(
                        medisinskArsak = null,
                        arbeidsrelatertArsak = null,
                    )
                } else {
                    null
                },
            behandlingsdager = null,
            gradert =
                if (it.type == PeriodeType.GRADERT) {
                    Gradert(
                        grad = it.grad ?: throw IllegalStateException("Gradert periode must have grad"),
                        reisetilskudd = false,
                    )
                } else {
                    null
                },
            reisetilskudd = false,
            avventendeInnspillTilArbeidsgiver = null,
        )
    }

private fun RegisterOppgaveValues.mapToMedisinskVurdering() =
    MedisinskVurdering(
        hovedDiagnose =
            hovedDiagnose?.let {
                Diagnose(
                    kode = it.kode,
                    system = it.system,
                    tekst = getDiagnoseText(it.system, it.kode),
                )
            },
        biDiagnoser =
            biDiagnoser?.map {
                Diagnose(
                    kode = it.kode,
                    system = it.system,
                    tekst = getDiagnoseText(it.system, it.kode),
                )
            } ?: emptyList(),
        annenFraversArsak = null,
        svangerskap = false,
        yrkesskade = false,
        yrkesskadeDato = null,
    )

fun <T> T.toPGObject() =
    PGobject().also {
        it.type = "json"
        it.value = objectMapper.writeValueAsString(this)
    }

private fun ResultSet.toDigitaliseringsoppgave(): OppgaveDbModel =
    OppgaveDbModel(
        oppgaveId = getString("oppgave_id"),
        fnr = getString("fnr"),
        journalpostId = getString("journalpost_id"),
        dokumentInfoId = getString("dokumentinfo_id"),
        opprettet = getTimestamp("opprettet").toInstant().atOffset(ZoneOffset.UTC),
        ferdigstilt = getTimestamp("ferdigstilt")?.toInstant()?.atOffset(ZoneOffset.UTC),
        tilbakeTilGosys = getBoolean("tilbake_til_gosys"),
        avvisingsgrunn = getString("avvisings_grunn"),
        sykmeldingId = UUID.fromString(getString("sykmelding_id")),
        type = getString("type"),
        sykmelding = getString("sykmelding")?.let { objectMapper.readValue(it, SykmeldingUnderArbeid::class.java) },
        dokumenter = getString("dokumenter").let { objectMapper.readValue<List<DokumentDbModel>>(it) },
        endretAv = getString("endret_av"),
        timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
        source = getString("source"),
    )

private fun ResultSet.toSykmeldingUnderArbeid(): SykmeldingUnderArbeid? {
    val sykmeldingJsonb: String = getString("sykmelding") ?: return null

    return objectMapper.readValue(sykmeldingJsonb, SykmeldingUnderArbeid::class.java)
}
