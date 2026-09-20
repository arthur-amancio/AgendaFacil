package br.com.agendafacilpro.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DemoDataMigrationTest {

    @Test
    void migrationDisablesTheKnownCredentialAndTheDemoTenant() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V7__disable_legacy_demo_access.sql"));

        assertThat(migration)
                .contains("SET enabled = false")
                .contains("password_hash = '!disabled-demo-account!'")
                .contains("LOWER(email) = 'admin@demo.local'")
                .contains("OR establishment_id IN")
                .contains("WHERE slug = 'agenda-demo'")
                .contains("SET active = false");
    }
}
