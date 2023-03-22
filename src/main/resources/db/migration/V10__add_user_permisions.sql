DO
$$
BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'syk-dig-oppgavelytter')
        THEN
            GRANT SELECT ON ALL TABLES IN SCHEMA public TO "syk-dig-oppgavelytter";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO "syk-dig-oppgavelytter";
END IF;
END
$$;