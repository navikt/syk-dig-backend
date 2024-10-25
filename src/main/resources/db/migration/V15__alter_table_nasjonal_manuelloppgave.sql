ALTER TABLE nasjonal_manuelloppgave
    ADD COLUMN id UUID DEFAULT gen_random_uuid();

ALTER TABLE nasjonal_manuelloppgave
DROP CONSTRAINT nasjonal_manuelloppgave_pkey;

ALTER TABLE nasjonal_manuelloppgave
    ALTER COLUMN sykmelding_id SET NOT NULL;

ALTER TABLE nasjonal_manuelloppgave
    ADD PRIMARY KEY (id);
