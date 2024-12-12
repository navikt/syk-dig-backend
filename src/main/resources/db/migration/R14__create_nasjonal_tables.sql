create table nasjonal_manuelloppgave
(
    id                    UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    sykmelding_id         VARCHAR   NOT NULL,
    journalpost_id        VARCHAR   NOT NULL,
    fnr                   VARCHAR   NULL,
    aktor_id              VARCHAR   NULL,
    dokument_info_id      VARCHAR   NULL,
    dato_opprettet        TIMESTAMP with time zone NULL,
    oppgave_id            INT,
    ferdigstilt           boolean   NOT NULL,
    papir_sm_registrering JSONB     NULL,
    utfall                VARCHAR   NULL,
    ferdigstilt_av        VARCHAR   NULL,
    dato_ferdigstilt      TIMESTAMP with time zone NULL,
    avvisningsgrunn       VARCHAR   NULL
);

create table nasjonal_sykmelding
(
    id               UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    sykmelding_id    VARCHAR                  NOT NULL,
    sykmelding       JSONB                    NOT NULL,
    timestamp        TIMESTAMP with time zone NOT NULL,
    ferdigstilt_av   VARCHAR                  NULL,
    dato_ferdigstilt TIMESTAMP with time zone NULL
);

ALTER TABLE nasjonal_manuelloppgave
    ADD CONSTRAINT unique_sykmelding_id UNIQUE (sykmelding_id);

ALTER TABLE nasjonal_sykmelding
    ADD CONSTRAINT unique_sykmelding_id_timestamp UNIQUE (sykmelding_id, timestamp);

CREATE INDEX idx_sykmelding_id ON sykmelding(sykmelding_id);
