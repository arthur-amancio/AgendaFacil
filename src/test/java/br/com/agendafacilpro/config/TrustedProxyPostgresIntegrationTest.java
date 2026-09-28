package br.com.agendafacilpro.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.agendafacilpro.AgendaFacilProApplication;
import jakarta.servlet.http.HttpServletRequest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.address=0.0.0.0",
        classes = {AgendaFacilProApplication.class, TrustedProxyPostgresIntegrationTest.ProxyProbeConfiguration.class})
@ActiveProfiles("prod")
@Testcontainers
class TrustedProxyPostgresIntegrationTest {

    private static final String FORWARDED_CLIENT_IP = "198.51.100.27";

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
    private int port;

    @Test
    void localProxyControlsRemoteAddressAndHttpsState() throws Exception {
        HttpResponse<String> response = request("127.0.0.1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(FORWARDED_CLIENT_IP + "|true|https");
        assertThat(response.headers().firstValue("Strict-Transport-Security"))
                .hasValueSatisfying(value -> assertThat(value)
                        .contains("max-age=31536000")
                        .contains("includeSubDomains"));
    }

    @Test
    void untrustedOriginCannotSpoofRemoteAddressOrHttpsState() throws Exception {
        InetAddress nonLoopbackAddress = findNonLoopbackIpv4Address();

        HttpResponse<String> response = request(nonLoopbackAddress.getHostAddress());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .isEqualTo(nonLoopbackAddress.getHostAddress() + "|false|http")
                .doesNotContain(FORWARDED_CLIENT_IP);
        assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
    }

    private HttpResponse<String> request(String host) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + host + ":" + port + "/agenda/__proxy-probe"))
                .header("X-Forwarded-For", FORWARDED_CLIENT_IP)
                .header("X-Forwarded-Proto", "https")
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private InetAddress findNonLoopbackIpv4Address() throws Exception {
        return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                .filter(this::isUsableInterface)
                .flatMap(networkInterface -> Collections.list(networkInterface.getInetAddresses()).stream())
                .filter(Inet4Address.class::isInstance)
                .filter(address -> !address.isLoopbackAddress() && !address.isLinkLocalAddress())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Nenhum endereco IPv4 nao-loopback disponivel para o teste"));
    }

    private boolean isUsableInterface(NetworkInterface networkInterface) {
        try {
            return networkInterface.isUp() && !networkInterface.isLoopback();
        } catch (Exception exception) {
            return false;
        }
    }

    @TestConfiguration
    static class ProxyProbeConfiguration {

        @Bean
        ProxyProbeController proxyProbeController() {
            return new ProxyProbeController();
        }
    }

    @RestController
    static class ProxyProbeController {

        @GetMapping("/agenda/__proxy-probe")
        String probe(HttpServletRequest request) {
            return request.getRemoteAddr() + "|" + request.isSecure() + "|" + request.getScheme();
        }
    }
}
