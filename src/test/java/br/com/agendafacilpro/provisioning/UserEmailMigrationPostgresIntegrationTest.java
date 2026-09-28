package br.com.agendafacilpro.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class UserEmailMigrationPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Test
    void v10CreatesCaseInsensitiveUniqueIndex() {
        String schema = "email_index_" + SEQUENCE.incrementAndGet();
        try {
            flyway(schema, null).migrate();
            JdbcTemplate jdbc = jdbc();

            Integer indexCount = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM pg_indexes
                    WHERE schemaname=? AND indexname='uk_users_app_email_ci'
                    """, Integer.class, schema);
            assertThat(indexCount).isEqualTo(1);

            Long nextEstablishmentId = jdbc.queryForObject(
                    "INSERT INTO " + schema + ".establishments(name,slug,whatsapp,active) VALUES ('Sequence','sequence-v10','5517999999999',false) RETURNING id",
                    Long.class);
            assertThat(nextEstablishmentId).isGreaterThan(1L);

            jdbc.update("INSERT INTO " + schema + ".establishments(id,name,slug,whatsapp,active) VALUES (9101,'Piloto','email-index-piloto','5517999999999',false)");
            jdbc.update("INSERT INTO " + schema + ".users_app(establishment_id,name,email,password_hash,role,enabled) VALUES (9101,'Owner','Owner@Example.test','hash','OWNER',true)");
            assertThatThrownBy(() -> jdbc.update("INSERT INTO " + schema + ".users_app(establishment_id,name,email,password_hash,role,enabled) VALUES (9101,'Outro','owner@example.test','hash','OWNER',true)"))
                    .isInstanceOf(Exception.class);
        } finally {
            jdbc().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void v10FailsWithoutChangingCaseInsensitiveDuplicates() {
        String schema = "email_preflight_" + SEQUENCE.incrementAndGet();
        try {
            flyway(schema, MigrationVersion.fromVersion("9")).migrate();
            JdbcTemplate jdbc = jdbc();
            jdbc.update("INSERT INTO " + schema + ".establishments(id,name,slug,whatsapp,active) VALUES (9201,'Piloto','email-preflight-piloto','5517999999999',false)");
            jdbc.update("INSERT INTO " + schema + ".users_app(establishment_id,name,email,password_hash,role,enabled) VALUES (9201,'Owner A','Owner@Example.test','hash','OWNER',true)");
            jdbc.update("INSERT INTO " + schema + ".users_app(establishment_id,name,email,password_hash,role,enabled) VALUES (9201,'Owner B','owner@example.test','hash','OWNER',true)");

            assertThatThrownBy(() -> flyway(schema, null).migrate())
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("case-insensitive duplicate user emails exist");

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + ".users_app WHERE lower(email)='owner@example.test'", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE schemaname=? AND indexname='uk_users_app_email_ci'", Integer.class, schema)).isZero();
        } finally {
            jdbc().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private Flyway flyway(String schema, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }
}
