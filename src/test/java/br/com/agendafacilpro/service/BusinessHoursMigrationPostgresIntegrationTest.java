package br.com.agendafacilpro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class BusinessHoursMigrationPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Test
    void v9MigratesFromV8AndBackfillsSevenDaysPerExistingTenant() {
        String schema = "hours_" + SEQUENCE.incrementAndGet();
        try {
            Flyway base = flyway(schema, MigrationVersion.fromVersion("8"));
            base.migrate();
            JdbcTemplate jdbc = jdbc();
            jdbc.update("INSERT INTO " + schema + ".establishments(id,name,slug,whatsapp,active) VALUES (9001,'Piloto','piloto','5517999999999',true)");

            flyway(schema, null).migrate();

            Integer tenants = jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + ".establishments", Integer.class);
            Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + ".establishment_business_hours", Integer.class);
            Integer defaults = jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + ".establishment_business_hours WHERE is_open AND opening_time=TIME '08:00' AND closing_time=TIME '18:00'", Integer.class);
            assertThat(rows).isEqualTo(tenants * 7);
            assertThat(defaults).isEqualTo(rows);
        } finally {
            jdbc().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void databaseRejectsDuplicateInvalidAndCrossTenantRows() {
        String schema = "constraints_" + SEQUENCE.incrementAndGet();
        try {
            flyway(schema, null).migrate();
            JdbcTemplate jdbc = jdbc();
            assertThatThrownBy(() -> jdbc.update("INSERT INTO " + schema + ".establishment_business_hours(establishment_id,day_of_week,is_open,opening_time,closing_time) VALUES (1,'MONDAY',true,TIME '09:00',TIME '17:00')"))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO " + schema + ".establishment_business_hours(establishment_id,day_of_week,is_open,opening_time,closing_time) VALUES (1,'INVALID',false,NULL,NULL)"))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO " + schema + ".establishment_business_hours(establishment_id,day_of_week,is_open,opening_time,closing_time) VALUES (999999,'MONDAY',false,NULL,NULL)"))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE " + schema + ".establishment_business_hours SET opening_time=TIME '18:00', closing_time=TIME '08:00' WHERE establishment_id=1 AND day_of_week='MONDAY'"))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbc().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private Flyway flyway(String schema, MigrationVersion target) {
        var config = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).schemas(schema).defaultSchema(schema);
        if (target != null) config.target(target);
        return config.load();
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        return new JdbcTemplate(dataSource);
    }
}
