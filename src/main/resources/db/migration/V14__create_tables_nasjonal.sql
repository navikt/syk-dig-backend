create table nasjonal_manuelloppgave
(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), -- Auto-generated UUID
    sykmelding_id VARCHAR NOT NULL,
    journalpost_id        VARCHAR  NOT NULL,
    fnr                   VARCHAR  NULL,
    aktor_id              VARCHAR  NULL,
    dokument_info_id      VARCHAR  NULL,
    dato_opprettet        TIMESTAMP NULL,
    oppgave_id            INT,
    ferdigstilt           boolean   NOT NULL,
    papir_sm_registrering JSONB     NULL,
    utfall                VARCHAR   NULL,
    ferdigstilt_av        VARCHAR,
    dato_ferdigstilt      TIMESTAMP,
    avvisningsgrunn       VARCHAR   NULL
);

create table nasjonal_sykmelding
(
    sykmelding_id    VARCHAR,
    sykmelding       JSONB                    not null,
    timestamp        TIMESTAMP with time zone not null,
    ferdigstilt_av   VARCHAR,
    dato_ferdigstilt TIMESTAMP,
    primary key (sykmelding_id, timestamp)
);



