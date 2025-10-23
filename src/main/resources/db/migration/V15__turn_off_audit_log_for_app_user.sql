DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'syk-dig-backend-db-instance')
        THEN
            ALTER USER "syk-dig-backend-db-instance" IN DATABASE "syk-dig-backend" SET pgaudit.log TO 'none';
        END IF;
    END
$$;
