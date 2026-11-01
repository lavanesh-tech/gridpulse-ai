package com.gridpulse;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** One real pgvector Postgres container shared by all integration tests (started once per JVM). */
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        // Test-only signing key; real deployments supply GRIDPULSE_JWT_SECRET from a secret store.
        registry.add("gridpulse.security.jwt-secret", () -> "integration-test-signing-key-not-a-real-secret");
        registry.add("gridpulse.security.requests-per-minute", () -> "100000");
        registry.add("gridpulse.security.login-requests-per-minute", () -> "100000");
    }
}
