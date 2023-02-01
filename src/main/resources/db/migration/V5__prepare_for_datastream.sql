
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
            (SELECT 1 from pg_roles where rolname = 'syk-dig-datastream-user')
        THEN
            alter user "syk-dig-datastream-user" with replication;
            GRANT SELECT ON ALL TABLES IN SCHEMA public TO "syk-dig-datastream-user";
            GRANT USAGE ON SCHEMA public TO "syk-dig-datastream-user";
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO "syk-dig-datastream-user";
        END IF;
    END
$$;
