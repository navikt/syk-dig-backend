alter user "syk-dig-backend-db-instance" with replication;
create publication syk_dig_publication for ALL TABLES;
SELECT PG_CREATE_LOGICAL_REPLICATION_SLOT
           ('syk_dig_replication', 'pgoutput');
alter user "syk-dig-datastream-user" with replication;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO "datastream-syk-dig-user";
GRANT USAGE ON SCHEMA public TO "datastream-syk-dig-user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO "datastream-syk-dig-user";