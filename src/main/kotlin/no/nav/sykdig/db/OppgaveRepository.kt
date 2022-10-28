package no.nav.sykdig.db

import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Periode
import no.nav.syfo.model.UtenlandskSykmelding
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.PeriodeType
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidValues
import no.nav.sykdig.model.DigitaliseringsoppgaveDbModel
import no.nav.sykdig.model.Sykmelding
import no.nav.sykdig.model.SykmeldingUnderArbeid
import no.nav.sykdig.objectMapper
import no.nav.sykdig.utils.toOffsetDateTimeAtNoon
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
                .addValue(
                    "ferdigstilt",
                    digitaliseringsoppgave.ferdigstilt?.let { Timestamp.from(digitaliseringsoppgave.ferdigstilt.toInstant()) },
                )
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
            """SELECT o.oppgave_id,
                           fnr,
                           journalpost_id,
                           dokumentinfo_id,
                           opprettet,
                           ferdigstilt,
                           sykmelding_id,
                           type,
                           s.sykmelding,
                           endret_av,
                           timestamp
                    FROM oppgave AS o
                             LEFT OUTER JOIN sykmelding AS s ON o.oppgave_id = s.oppgave_id
                        AND s.timestamp = (SELECT MAX(timestamp)
                                           FROM sykmelding
                                           WHERE s.oppgave_id = o.oppgave_id)
                    WHERE o.oppgave_id = :oppgave_id;
            """,
            mapOf("oppgave_id" to oppgaveId)
        ) { resultSet, _ ->
            resultSet.toDigitaliseringsoppgave()
        }.firstOrNull()
    }

    @Transactional
    fun updateOppgave(
        oppgaveId: String,
        values: SykmeldingUnderArbeidValues,
        ident: String,
        ferdigstilles: Boolean,
    ) {
        val oppgave = getOppgave(oppgaveId) ?: throw IllegalStateException("Oppgave can't not exist at this point")
        val sykmelding = toSykmelding(oppgave, values)

        if (ferdigstilles) {
            namedParameterJdbcTemplate.update(
                """
                UPDATE oppgave
                SET ferdigstilt = :ferdigstilt
                WHERE oppgave_id = :oppgave_id
                """.trimIndent(),
                mapOf(
                    "oppgave_id" to oppgaveId,
                    "ferdigstilt" to Timestamp.from(Instant.now()),
                )
            )
        }
        namedParameterJdbcTemplate.update(
            """
                INSERT INTO sykmelding(sykmelding_id, oppgave_id, type, sykmelding, endret_av, timestamp)
                VALUES (:sykmelding_id, :oppgave_id, :type, :sykmelding, :endret_av, :timestamp)
            """.trimIndent(),
            mapOf(
                "sykmelding_id" to oppgave.sykmeldingId.toString(),
                "oppgave_id" to oppgaveId,
                "type" to oppgave.type,
                "endret_av" to ident,
                "timestamp" to Timestamp.from(Instant.now()),
                "sykmelding" to sykmelding.toPGObject(),
            )
        )
    }
}

fun toSykmelding(
    oppgave: DigitaliseringsoppgaveDbModel,
    values: SykmeldingUnderArbeidValues,
): SykmeldingUnderArbeid {
    if (oppgave.sykmelding == null) {
        val msgId = UUID.randomUUID().toString()
        return SykmeldingUnderArbeid(
            sykmelding = Sykmelding(
                id = oppgave.sykmeldingId.toString(),
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
                behandletTidspunkt = values.behandletTidspunkt.toOffsetDateTimeAtNoon(),
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
            utenlandskSykmelding = if (values.skrevetLand != null) UtenlandskSykmelding(
                land = values.skrevetLand,
                andreRelevanteOpplysninger = values.harAndreRelevanteOpplysninger ?: false,
            ) else null,
        )
    } else {
        val sykmelding: SykmeldingUnderArbeid = oppgave.sykmelding
        sykmelding.sykmelding.medisinskVurdering = values.mapToMedisinskVurdering()
        sykmelding.sykmelding.perioder = values.perioder.mapToPerioder()
        sykmelding.sykmelding.behandletTidspunkt = values.behandletTidspunkt.toOffsetDateTimeAtNoon()
        sykmelding.fnrPasient = values.fnrPasient
        sykmelding.utenlandskSykmelding = if (values.skrevetLand != null) UtenlandskSykmelding(
            land = values.skrevetLand,
            andreRelevanteOpplysninger = values.harAndreRelevanteOpplysninger ?: false
        ) else null
        return sykmelding
    }
}

private fun List<PeriodeInput>?.mapToPerioder(): List<Periode>? = this?.map {
    Periode(
        fom = it.fom,
        tom = it.tom,
        aktivitetIkkeMulig = if (it.type == PeriodeType.AKTIVITET_IKKE_MULIG) AktivitetIkkeMulig(
            medisinskArsak = null,
            arbeidsrelatertArsak = null,
        ) else null,
        behandlingsdager = if (it.type == PeriodeType.BEHANDLINGSDAGER) 1 else null,
        gradert = if (it.type == PeriodeType.GRADERT) Gradert(
            grad = it.grad ?: throw IllegalStateException("Gradert periode must have grad"),
            reisetilskudd = false,
        ) else null,
        reisetilskudd = it.type == PeriodeType.REISETILSKUDD,
        // TODO: implemntere tekstfelt i frontend
        avventendeInnspillTilArbeidsgiver = if (it.type === PeriodeType.AVVENTENDE) "TODO: må komme fra frontend" else null,
    )
}

private fun SykmeldingUnderArbeidValues.mapToMedisinskVurdering() = MedisinskVurdering(
    hovedDiagnose = hovedDiagnose?.let {
        Diagnose(
            kode = it.kode,
            system = it.system,
            tekst = null, // TODO enhance
        )
    },
    biDiagnoser = biDiagnoser?.map {
        Diagnose(
            kode = it.kode,
            system = it.system,
            tekst = null, // TODO enhance
        )
    } ?: emptyList(),
    annenFraversArsak = null,
    svangerskap = false,
    yrkesskade = false,
    yrkesskadeDato = null,
)

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
