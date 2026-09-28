package br.com.agendafacilpro.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ApplicationYamlSecurityTest {

    @Test
    void commonConfigurationIsSafeAndDoesNotSelectAnEnvironment() throws Exception {
        String yaml = read("application.yml");

        assertThat(yaml)
                .doesNotContain("profiles:\n    active:")
                .doesNotContain("jdbc:postgresql://localhost")
                .doesNotContain("DB_PASSWORD")
                .contains("cache: true")
                .contains("name: AGENDAFACIL_SESSION")
                .contains("http-only: true")
                .contains("secure: true")
                .contains("same-site: strict")
                .contains("include-message: never")
                .contains("include-stacktrace: never")
                .contains("include-binding-errors: never")
                .contains("enabled: false");
    }

    @Test
    void developmentConfigurationContainsOnlyLocalConveniences() throws Exception {
        String yaml = read("application-dev.yml");

        assertThat(yaml)
                .contains("jdbc:postgresql://localhost:5432/agendafacil_pro")
                .contains("${DB_USERNAME:agendafacil}")
                .contains("${APP_TIME_ZONE:America/Sao_Paulo}")
                .contains("cache: false")
                .contains("secure: false")
                .doesNotContain("postdba");
    }

    @Test
    void productionConfigurationHasNoDevelopmentFallbacks() throws Exception {
        String yaml = read("application-prod.yml");

        assertThat(yaml)
                .contains("url: ${DB_URL:}")
                .contains("username: ${DB_USERNAME:}")
                .contains("password: ${DB_PASSWORD:}")
                .contains("time-zone: ${APP_TIME_ZONE:}")
                .contains("cache: true")
                .contains("secure: true")
                .contains("enabled: false")
                .doesNotContain("localhost")
                .doesNotContain("agendafacil}")
                .doesNotContain("America/Sao_Paulo")
                .doesNotContain("postdba");
    }

    private String read(String fileName) throws Exception {
        return Files.readString(Path.of("src/main/resources", fileName));
    }
}
