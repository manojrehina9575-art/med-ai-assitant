package com.medai;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests.
 * Starts a real PostgreSQL container via Testcontainers and wires it into Spring.
 * Flyway migrations run automatically against this container.
 *
 * <p>Extend this class for any test that needs the full Spring context + database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    /**
     * Must be the pgvector image, not plain postgres: V5 runs {@code CREATE EXTENSION vector} and
     * V8 creates an HNSW index, neither of which exists in {@code postgres:16-alpine}. This is the
     * same image docker-compose uses, so tests migrate against what production runs.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("medai_test")
            .withUsername("test")
            .withPassword("test");

    /** Matches the application's runtime role; V11 creates it with this password. */
    protected static final String APP_DB_USERNAME = "medai_app";
    protected static final String APP_DB_PASSWORD = "test_app_pw";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);

        // Mirror production's split: Flyway migrates as the container's superuser, the application
        // connects as the restricted role created by V11. Running tests as the superuser would
        // silently bypass row-level security and make isolation tests prove nothing.
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.flyway.placeholders.appDbUser", () -> APP_DB_USERNAME);
        registry.add("spring.flyway.placeholders.appDbPassword", () -> APP_DB_PASSWORD);

        registry.add("spring.datasource.username", () -> APP_DB_USERNAME);
        registry.add("spring.datasource.password", () -> APP_DB_PASSWORD);
    }
}
