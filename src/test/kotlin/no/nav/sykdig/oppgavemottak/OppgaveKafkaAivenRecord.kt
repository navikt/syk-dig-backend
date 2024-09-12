package no.nav.sykdig.oppgavemottak

import java.time.LocalDateTime

val kafkarecord =
    OppgaveKafkaAivenRecord(
        hendelse =
            Hendelse(
                hendelsestype = Hendelsestype.OPPGAVE_ENDRET,
                tidspunkt = LocalDateTime.now(),
            ),
        oppgave =
            Oppgave(
                oppgaveId = 1,
                bruker =
                    Bruker(
                        "1",
                        identType = IdentType.NPID,
                    ),
                kategorisering =
                    Kategorisering(
                        "tema",
                        oppgavetype = " ",
                        behandlingstema = "",
                        behandlingstype = "",
                    ),
            ),
    )
