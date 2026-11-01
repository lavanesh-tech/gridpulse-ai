package com.gridpulse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the full app against a real pgvector Postgres and checks the schema migrated. */
@SpringBootTest
@Testcontainers
class FlywayMigrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired JdbcTemplate jdbc;

    @Test
    void foundationTablesAndVectorExtensionExist() {
        Integer tables = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN
                ('tenant','fleet','vehicle','grid_connection','charging_station','charger','tariff_period','audit_event')
                """, Integer.class);
        assertThat(tables).isEqualTo(8);

        Integer vector = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
        assertThat(vector).isEqualTo(1);
    }
}
