drop table if exists nasjonal_manuelloppgave;
drop table if exists nasjonal_sykmelding;

create table nasjonal_manuelloppgave
(
    id                    UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    sykmelding_id         VARCHAR   NOT NULL,
    journalpost_id        VARCHAR   NOT NULL,
    fnr                   VARCHAR   NULL,
    aktor_id              VARCHAR   NULL,
    dokument_info_id      VARCHAR   NULL,
    dato_opprettet        TIMESTAMP NULL,
    oppgave_id            INT,
    ferdigstilt           boolean   NOT NULL,
    papir_sm_registrering JSONB     NULL,
    utfall                VARCHAR   NULL,
    ferdigstilt_av        VARCHAR   NULL,
    dato_ferdigstilt      TIMESTAMP NULL,
    avvisningsgrunn       VARCHAR   NULL
);

create table nasjonal_sykmelding
(
    sykmelding_id    VARCHAR NOT NULL,
    sykmelding       JSONB                    NOT NULL,
    timestamp        TIMESTAMP with time zone NOT NULL,
    ferdigstilt_av   VARCHAR null,
    dato_ferdigstilt TIMESTAMP,
    primary key (sykmelding_id, timestamp)
);

ALTER TABLE nasjonal_manuelloppgave
    ADD CONSTRAINT unique_sykmelding_id UNIQUE (sykmelding_id);