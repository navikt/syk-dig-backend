package no.nav.sykdig.oppgavemottak

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class OppgaveKafkaAivenRecord(
    val hendelse: Hendelse,
    val oppgave: Oppgave,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Oppgave(
    val oppgaveId: Long,
    val bruker: Bruker?,
    val kategorisering: Kategorisering,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Bruker(
    val ident: String,
    val identType: IdentType,
)

enum class IdentType {
    FOLKEREGISTERIDENT,
    NPID,
    ORGNR,
    SAMHANDLERNR,
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Kategorisering(
    val tema: String,
    val oppgavetype: String,
    val behandlingstema: String?,
    val behandlingstype: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Hendelse(
    val hendelsestype: Hendelsestype,
    val tidspunkt: LocalDateTime?,
)

enum class Hendelsestype {
    OPPGAVE_OPPRETTET,
    OPPGAVE_ENDRET,
    OPPGAVE_FERDIGSTILT,
    OPPGAVE_FEILREGISTRERT,
}
