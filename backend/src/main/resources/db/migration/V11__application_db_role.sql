-- ===========================================
-- V11: Give the application a non-superuser role, so RLS applies to it
-- ===========================================
-- V9 added FORCE ROW LEVEL SECURITY, which subjects a table's OWNER to its policies. That was not
-- enough: the credentials the application uses are the ones POSTGRES_USER creates, and that role is
-- a SUPERUSER. Superusers bypass row-level security unconditionally — FORCE does not apply to them.
-- Verified directly: acting as tenant Beta, a raw query still returned tenant Alpha's patients, and
-- an INSERT carrying Alpha's tenant_id into Beta's session succeeded.
--
-- So the application now connects as `medai_app`: LOGIN, no superuser, no ownership, DML only.
-- Flyway keeps using the owner account (spring.flyway.user) to run migrations, because a role that
-- cannot bypass RLS also cannot reliably perform data migrations.
--
-- The password comes from the spring.flyway.placeholders.appDbPassword property, which is wired to
-- DB_APP_PASSWORD. Change it from the default before exposing this to anything real.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${appDbUser}') THEN
        EXECUTE format('ALTER ROLE %I LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD %L',
                       '${appDbUser}', '${appDbPassword}');
    ELSE
        EXECUTE format('CREATE ROLE %I LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD %L',
                       '${appDbUser}', '${appDbPassword}');
    END IF;
END $$;

GRANT USAGE ON SCHEMA public TO ${appDbUser};

-- DML only: no DDL, no TRUNCATE (which bypasses RLS), no ownership.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${appDbUser};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ${appDbUser};
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO ${appDbUser};

-- Tables created by later migrations are granted automatically, so a new table is never
-- accidentally unreachable by the application — or reachable without a policy.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${appDbUser};
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO ${appDbUser};
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO ${appDbUser};

-- Flyway's own history table is the owner's business only.
REVOKE ALL ON flyway_schema_history FROM ${appDbUser};
