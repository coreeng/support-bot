-- Create read-only user for Datadog PostgreSQL integration
-- Set password via: -Dflyway.placeholders.DATADOG_PASSWORD=xxx

CREATE USER datadog WITH PASSWORD '${DATADOG_PASSWORD}';
GRANT USAGE ON SCHEMA public TO datadog;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO datadog;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO datadog;
