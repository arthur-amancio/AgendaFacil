package br.com.agendafacilpro.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.Shutdown;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.server.context.WebServerGracefulShutdownLifecycle;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "spring.datasource.hikari.connection-timeout=1000",
                "spring.datasource.hikari.validation-timeout=1000"
        })
@ActiveProfiles("prod")
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
class HealthReadinessPostgresIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void productionProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("APP_TIME_ZONE", () -> "America/Sao_Paulo");
    }

    @LocalServerPort
    private int applicationPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private ServerProperties serverProperties;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @Order(1)
    void exposesOnlyMinimalUnauthenticatedHealthOnTheInternalManagementServer() throws Exception {
        HttpResponse<String> overall = managementGet("/actuator/health");
        HttpResponse<String> liveness = managementGet("/actuator/health/liveness");
        HttpResponse<String> readiness = managementGet("/actuator/health/readiness");

        assertMinimalHealth(overall, 200, "UP");
        assertMinimalHealth(liveness, 200, "UP");
        assertMinimalHealth(readiness, 200, "UP");
        assertThat(liveness.headers().firstValue("Location")).isEmpty();
        assertThat(liveness.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(liveness.headers().firstValue("WWW-Authenticate")).isEmpty();

        for (String path : List.of(
                "/actuator",
                "/actuator/env",
                "/actuator/beans",
                "/actuator/configprops",
                "/actuator/mappings",
                "/actuator/metrics")) {
            assertUnavailable(managementGet(path));
        }
        assertUnavailable(managementPost("/actuator/shutdown"));

        HttpResponse<String> applicationHealth = get(applicationPort, "/actuator/health");
        assertThat(applicationHealth.statusCode()).isNotEqualTo(200);
        assertThat(applicationHealth.body()).doesNotContain("\"status\"");

        assertThat(serverProperties.getShutdown()).isEqualTo(Shutdown.GRACEFUL);
        assertThat(applicationContext.getBeansOfType(WebServerGracefulShutdownLifecycle.class)).isNotEmpty();
    }

    @Test
    @Order(2)
    void databaseFailureMakesReadinessDownButKeepsLivenessUp() throws Exception {
        POSTGRES.stop();

        HttpResponse<String> readiness = awaitReadinessUnavailable();
        assertThat(readiness.statusCode()).isEqualTo(503);
        assertThat(readiness.body()).isIn("{\"status\":\"DOWN\"}", "{\"status\":\"OUT_OF_SERVICE\"}");
        assertNoSensitiveDetails(readiness.body());

        HttpResponse<String> liveness = managementGet("/actuator/health/liveness");
        assertMinimalHealth(liveness, 200, "UP");
    }

    private HttpResponse<String> awaitReadinessUnavailable() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        HttpResponse<String> lastResponse = null;
        while (System.nanoTime() < deadline) {
            lastResponse = managementGet("/actuator/health/readiness");
            if (lastResponse.statusCode() == 503) {
                return lastResponse;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Readiness permaneceu disponivel apos a parada do PostgreSQL: "
                + (lastResponse == null ? "sem resposta" : lastResponse.body()));
    }

    private void assertMinimalHealth(HttpResponse<String> response, int statusCode, String status) {
        assertThat(response.statusCode()).isEqualTo(statusCode);
        assertThat(response.body()).isEqualTo("{\"status\":\"" + status + "\"}");
        assertNoSensitiveDetails(response.body());
    }

    private void assertNoSensitiveDetails(String body) {
        assertThat(body)
                .doesNotContain("components")
                .doesNotContain("details")
                .doesNotContain("jdbc:")
                .doesNotContain(POSTGRES.getUsername())
                .doesNotContain(POSTGRES.getPassword())
                .doesNotContain("exception")
                .doesNotContain("stacktrace");
    }

    private void assertUnavailable(HttpResponse<String> response) {
        assertThat(response.statusCode()).isIn(302, 401, 403, 404, 405);
        assertNoSensitiveDetails(response.body());
    }

    private HttpResponse<String> managementGet(String path) throws Exception {
        return get(managementPort, path);
    }

    private HttpResponse<String> managementPost(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(managementPort, path))
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(port, path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(int port, String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
