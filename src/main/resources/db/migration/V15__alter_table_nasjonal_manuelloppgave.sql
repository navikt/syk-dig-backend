ALTER TABLE nasjonal_manuelloppgave (
    ADD COLUMN id UUID PRIMARY KEY DEFAULT gen_random_uuid(), -- Auto-generated UUID
    sykmelding_id VARCHAR NOT NULL
    );
