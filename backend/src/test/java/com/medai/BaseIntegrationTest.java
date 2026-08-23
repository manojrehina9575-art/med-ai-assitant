package com.medai;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base class for integration tests.
 * Starts a real PostgreSQL container via Testcontainers and wires it into Spring.
 * Flyway migrations run automatically against this container.
 *
 * <p>Extend this class for any test that needs the full Spring context + database.
 *
 * <p>Set {@code MEDAI_TEST_JDBC_URL} (with {@code MEDAI_TEST_DB_USER} /
 * {@code MEDAI_TEST_DB_PASSWORD}) to run against a database that already exists instead. The CI
 * workflow already stands up a {@code pgvector/pgvector:pg16} service container and then ignored
 * it, because Testcontainers unconditionally started a second one — and in any environment where
 * the test JVM cannot reach a Docker daemon, that was not merely wasteful but fatal. The external
 * database is wiped and re-migrated on entry, so a shared instance behaves like a fresh one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    /** Matches the application's runtime role; V11 creates it with this password. */
    protected static final String APP_DB_USERNAME = "medai_app";
    protected static final String APP_DB_PASSWORD = "test_app_pw";

    private static final String EXTERNAL_URL = System.getenv("MEDAI_TEST_JDBC_URL");
    private static final String EXTERNAL_USER =
            System.getenv().getOrDefault("MEDAI_TEST_DB_USER", "medai");
    private static final String EXTERNAL_PASSWORD =
            System.getenv().getOrDefault("MEDAI_TEST_DB_PASSWORD", "medai_secret");

    /**
     * Must be the pgvector image, not plain postgres: V5 runs {@code CREATE EXTENSION vector} and
     * V8 creates an HNSW index, neither of which exists in {@code postgres:16-alpine}. This is the
     * same image docker-compose uses, so tests migrate against what production runs.
     *
     * <p>Started once per JVM in a static initializer rather than managed by {@code @Testcontainers}
     * / {@code @Container}. That extension stops the container after every test class, but every
     * subclass here shares one cached Spring context — so the pool kept dialling the first class's
     * port while later classes booted fresh containers on new ones, and everything after the first
     * class failed with "connection refused". Ryuk removes this container when the JVM exits.
     */
    static final PostgreSQLContainer<?> postgres = EXTERNAL_URL != null ? null
            : new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("medai_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        if (postgres != null) {
            postgres.start();
        } else {
            resetExternalDatabase();
        }
    }

    /**
     * Returns the external database to the state a fresh container would be in.
     *
     * <p>Dropping the schema is not enough on its own: V11 creates {@code medai_app} as a cluster
     * role, which outlives the schema, and leaving it behind means the second run migrates against
     * a role whose password no longer matches. Both go.
     */
    private static void resetExternalDatabase() {
        try (Connection connection = DriverManager.getConnection(
                EXTERNAL_URL, EXTERNAL_USER, EXTERNAL_PASSWORD);
             Statement statement = connection.createStatement()) {

            statement.execute("DROP SCHEMA IF EXISTS public CASCADE");
            statement.execute("CREATE SCHEMA public");
            statement.execute("GRANT ALL ON SCHEMA public TO " + EXTERNAL_USER);
            statement.execute("GRANT ALL ON SCHEMA public TO public");
            statement.execute("DROP OWNED BY " + APP_DB_USERNAME + " CASCADE");
            statement.execute("DROP ROLE IF EXISTS " + APP_DB_USERNAME);
        } catch (SQLException e) {
            // A missing role is expected on the very first run; anything else is not.
            if (!"42704".equals(e.getSQLState())) {
                throw new IllegalStateException(
                        "Could not reset the external test database at " + EXTERNAL_URL, e);
            }
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String url = postgres != null ? postgres.getJdbcUrl() : EXTERNAL_URL;
        String ownerUser = postgres != null ? postgres.getUsername() : EXTERNAL_USER;
        String ownerPassword = postgres != null ? postgres.getPassword() : EXTERNAL_PASSWORD;

        registry.add("spring.datasource.url", () -> url);

        // Mirror production's split: Flyway migrates as the container's superuser, the application
        // connects as the restricted role created by V11. Running tests as the superuser would
        // silently bypass row-level security and make isolation tests prove nothing.
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> ownerUser);
        registry.add("spring.flyway.password", () -> ownerPassword);
        registry.add("spring.flyway.placeholders.appDbUser", () -> APP_DB_USERNAME);
        registry.add("spring.flyway.placeholders.appDbPassword", () -> APP_DB_PASSWORD);

        registry.add("spring.datasource.username", () -> APP_DB_USERNAME);
        registry.add("spring.datasource.password", () -> APP_DB_PASSWORD);
    }
}
