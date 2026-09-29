package br.com.agendafacilpro.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ProductionArtifactOperationsTest {

    @Test
    void systemdRunsTheJarAsAnUnprivilegedFailClosedService() throws Exception {
        String service = read("ops/systemd/agendafacil.service");

        assertThat(service)
                .contains("User=agendafacil")
                .contains("Group=agendafacil")
                .contains("WorkingDirectory=/opt/agendafacil/current")
                .contains("EnvironmentFile=/etc/agendafacil/agendafacil.env")
                .contains("ExecStart=/usr/bin/java -jar /opt/agendafacil/current/agendafacil-pro.jar")
                .contains("Restart=on-failure")
                .contains("RestartSec=5s")
                .contains("StartLimitIntervalSec=60s")
                .contains("StartLimitBurst=5")
                .contains("TimeoutStopSec=30s")
                .contains("KillSignal=SIGTERM")
                .contains("NoNewPrivileges=true")
                .contains("PrivateTmp=true")
                .contains("ProtectHome=true")
                .contains("ProtectSystem=strict")
                .contains("UMask=0027")
                .doesNotContain("User=root")
                .doesNotContain("8081");
    }

    @Test
    void caddyOnlyProxiesTheApplicationAndOverwritesForwardedHeaders() throws Exception {
        String caddyfile = read("ops/caddy/Caddyfile.example");

        assertThat(caddyfile)
                .contains("agenda.example.com")
                .contains("reverse_proxy 127.0.0.1:8080")
                .contains("header_up -Forwarded")
                .contains("header_up X-Forwarded-For {http.request.remote.host}")
                .contains("header_up X-Forwarded-Proto {http.request.scheme}")
                .contains("header_up X-Forwarded-Host {http.request.host}")
                .doesNotContain("8081")
                .doesNotContain("{http.request.header.X-Forwarded");
    }

    @Test
    void environmentExampleSelectsProductionWithoutEmbeddingCredentials() throws Exception {
        String environment = read("ops/env/agendafacil.env.example");

        assertThat(environment)
                .contains("SPRING_PROFILES_ACTIVE=prod")
                .contains("DB_URL=\n")
                .contains("DB_USERNAME=\n")
                .contains("DB_PASSWORD=\n")
                .contains("APP_TIME_ZONE=America/Sao_Paulo")
                .doesNotContain("admin123")
                .doesNotContain("jdbc:postgresql://localhost")
                .doesNotContain("dev,demo");
    }

    @Test
    void workflowBuildsValidatesAndUploadsOnlyTheNormalizedDistribution() throws Exception {
        String workflow = read(".github/workflows/ci.yml");

        assertThat(workflow)
                .contains("permissions:\n  contents: read")
                .contains("run: mvn clean verify")
                .contains("dist/agendafacil-pro.jar")
                .contains("sha256sum agendafacil-pro.jar > SHA256SUMS")
                .contains("sha256sum --check SHA256SUMS")
                .contains("Main-Class: org.springframework.boot.loader.launch.JarLauncher")
                .contains("Start-Class: br.com.agendafacilpro.AgendaFacilProApplication")
                .contains("actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1")
                .contains("name: agendafacil-pro-${{ github.sha }}")
                .contains("path: dist/")
                .contains("if-no-files-found: error");
    }

    @Test
    void deploymentRunbookDocumentsIntegrityReadinessRollbackAndNetworkBoundary() throws Exception {
        String deployment = read("docs/DEPLOYMENT.md");

        assertThat(deployment)
                .contains("/opt/agendafacil/releases/<release-id>")
                .contains("/etc/agendafacil/agendafacil.env")
                .contains("sha256sum --check SHA256SUMS")
                .contains("/actuator/health/liveness")
                .contains("/actuator/health/readiness")
                .contains("Rollback")
                .contains("80/443")
                .contains("Bloqueie acesso")
                .contains("externo a 8080, 8081")
                .contains("Rollback do JAR não desfaz migrations");
    }

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
