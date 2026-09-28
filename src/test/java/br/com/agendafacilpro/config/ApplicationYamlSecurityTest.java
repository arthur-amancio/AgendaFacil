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
                .doesNotContain("forward-headers-strategy")
                .doesNotContain("remoteip:")
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
                .contains("address: 127.0.0.1")
                .contains("forward-headers-strategy: native")
                .contains("redirect-context-root: false")
                .contains("remote-ip-header: X-Forwarded-For")
                .contains("protocol-header: X-Forwarded-Proto")
                .contains("host-header: X-Forwarded-Host")
                .contains("internal-proxies: 127.0.0.1/32")
                .doesNotContain("internal-proxies: ''")
                .doesNotContain("10.0.0.0/8")
                .doesNotContain("172.16.0.0/12")
                .doesNotContain("192.168.0.0/16")
                .doesNotContain("agendafacil}")
                .doesNotContain("America/Sao_Paulo")
                .doesNotContain("postdba");
    }

    private String read(String fileName) throws Exception {
        return Files.readString(Path.of("src/main/resources", fileName));
    }
}
