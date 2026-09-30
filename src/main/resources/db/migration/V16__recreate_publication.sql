DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'syk_dig_publication') THEN
            CREATE PUBLICATION syk_dig_publication FOR ALL TABLES;
        END IF;
    END
$$;
