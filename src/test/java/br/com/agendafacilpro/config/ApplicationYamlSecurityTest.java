package br.com.agendafacilpro.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ApplicationYamlSecurityTest {

    @Test
    void applicationYamlDoesNotContainLocalDatabasePassword() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(yaml).doesNotContain("postdba");
        assertThat(yaml).contains("password: ${DB_PASSWORD:}");
        assertThat(yaml).contains("name: AGENDAFACIL_SESSION");
        assertThat(yaml).contains("http-only: true");
        assertThat(yaml).contains("same-site: strict");
        assertThat(yaml).contains("on-profile: prod");
        assertThat(yaml).contains("secure: true");
        assertThat(yaml).contains("provisioning:");
        assertThat(yaml).contains("enabled: false");
    }
}
