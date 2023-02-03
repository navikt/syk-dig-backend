
DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'syk-dig-backend-db-instance')
        THEN
            alter user "syk-dig-backend-db-instance" with replication;
        END IF;
    END
$$;
DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'datastream-syk-dig-user')
        THEN
            alter user "datastream-syk-dig-user" with replication;
            GRANT SELECT ON ALL TABLES IN SCHEMA public TO "datastream-syk-dig-user";
            GRANT USAGE ON SCHEMA public TO "datastream-syk-dig-user";
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO "datastream-syk-dig-user";
        END IF;
    END
$$;
