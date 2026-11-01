package com.gridpulse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the full app against a real pgvector Postgres and checks the schema migrated. */
@SpringBootTest
class FlywayMigrationIT extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void allTablesAndExtensionsExist() {
        Integer tables = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN
                ('tenant','fleet','vehicle','grid_connection','charging_station','charger',
                 'tariff_period','audit_event','charging_session')
                """, Integer.class);
        assertThat(tables).isEqualTo(9);

        Integer extensions = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname IN ('vector','btree_gist')", Integer.class);
        assertThat(extensions).isEqualTo(2);
    }
}
