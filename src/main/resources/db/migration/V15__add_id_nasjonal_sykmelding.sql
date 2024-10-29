drop table if exists nasjonal_sykmelding;

create table nasjonal_sykmelding
(
    id               UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    sykmelding_id    VARCHAR                  NOT NULL,
    sykmelding       JSONB                    NOT NULL,
    timestamp        TIMESTAMP with time zone NOT NULL,
    ferdigstilt_av   VARCHAR                  NULL,
    dato_ferdigstilt TIMESTAMP                NULL
);

ALTER TABLE nasjonal_sykmelding
    ADD CONSTRAINT unique_sykmelding_id_timestamp UNIQUE (sykmelding_id, timestamp);