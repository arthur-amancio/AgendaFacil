package br.com.agendafacilpro.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ErrorPagesSecurityTest {

    @Test
    void customErrorPagesDoNotExposeTechnicalDetails() throws Exception {
        for (String page : new String[]{"403", "404", "500"}) {
            String html = Files.readString(Path.of("src/main/resources/templates/error/" + page + ".html"));

            assertThat(html)
                    .doesNotContain("stacktrace")
                    .doesNotContain("Exception")
                    .doesNotContain("org.springframework")
                    .doesNotContain("java.");
        }
    }

    @Test
    void genericErrorPageDoesNotAssumeAnyTenant() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/templates/error.html"));

        assertThat(html)
                .doesNotContain("agenda-demo")
                .doesNotContain("/agenda/")
                .contains("@{/login}");
    }
}
