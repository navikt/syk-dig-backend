package no.nav.sykdig.nasjonal.db.models

import jakarta.persistence.GeneratedValue
import no.nav.sykdig.nasjonal.models.PapirSmRegistering
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

@Table(name = "nasjonal_manuelloppgave")
data class NasjonalManuellOppgaveDAO(
    @Id
    @GeneratedValue(generator = "UUID")
    var id: UUID? = null,
    @Column("sykmelding_id")
    val sykmeldingId: String,
    @Column("journalpost_id")
    val journalpostId: String,
    @Column("fnr")
    val fnr: String? = null,
    @Column("aktor_id")
    val aktorId: String? = null,
    @Column("dokument_info_id")
    val dokumentInfoId: String? = null,
    @Column("dato_opprettet")
    val datoOpprettet: OffsetDateTime? = null,
    @Column("oppgave_id")
    val oppgaveId: Int? = null,
    @Column("ferdigstilt")
    val ferdigstilt: Boolean = false,
    @Column("papir_sm_registrering")
    val papirSmRegistrering: PapirSmRegistering,
    @Column("utfall")
    var utfall: String? = null,
    @Column("ferdigstilt_av")
    var ferdigstiltAv: String? = null,
    @Column("dato_ferdigstilt")
    var datoFerdigstilt: LocalDateTime? = null,
    @Column("avvisningsgrunn")
    var avvisningsgrunn: String? = null,
)


