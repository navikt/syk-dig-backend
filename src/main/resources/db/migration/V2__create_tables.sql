CREATE TABLE oppgave
(
    oppgave_id      VARCHAR primary key      not null,
    fnr             VARCHAR                  not null,
    journalpost_id  VARCHAR                  not null,
    dokumentinfo_id VARCHAR                  null,
    opprettet       TIMESTAMP with time zone not null,
    ferdigstilt     TIMESTAMP with time zone null
);

CREATE TABLE sykmelding
(
    sykmelding_id VARCHAR                  not null,
    oppgave_id    VARCHAR                  not null,
    type          VARCHAR                  not null,
    sykmelding    JSONB                    null,
    endret_av     VARCHAR                  not null,
    timestamp     TIMESTAMP with time zone not null,

    FOREIGN KEY (oppgave_id) REFERENCES oppgave (oppgave_id),
    CONSTRAINT sykmelding_pk PRIMARY KEY (sykmelding_id, timestamp)
);