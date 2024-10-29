ALTER TABLE nasjonal_manuelloppgave
    ADD CONSTRAINT unique_sykmelding_id UNIQUE (sykmelding_id);