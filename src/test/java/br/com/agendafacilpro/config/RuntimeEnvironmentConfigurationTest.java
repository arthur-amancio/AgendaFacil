package br.com.agendafacilpro.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RuntimeEnvironmentConfigurationTest {
    private static final String SECRET = "production-secret-must-not-leak";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(RuntimeEnvironmentConfiguration.class);

    @Test
    void validProductionStartupUsesOnlyExplicitValuesAndSafeDefaults() {
        validProduction(contextRunner).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("prod");
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://database.internal:5432/agendafacil");
            assertThat(context.getEnvironment().getProperty("spring.datasource.username")).isEqualTo("agenda_prod");
            assertThat(context.getEnvironment().getProperty("spring.datasource.password")).isEqualTo(SECRET);
            assertThat(context.getEnvironment().getProperty("app.time-zone")).isEqualTo("America/Sao_Paulo");
            assertThat(context.getEnvironment().getProperty("spring.datasource.hikari.connection-init-sql"))
                    .isEqualTo("SET TIME ZONE 'America/Sao_Paulo'");
            assertThat(context.getEnvironment().getProperty("spring.thymeleaf.cache", Boolean.class)).isTrue();
            assertThat(context.getEnvironment().getProperty("server.servlet.session.cookie.secure", Boolean.class)).isTrue();
            assertThat(context.getEnvironment().getProperty("app.provisioning.enabled", Boolean.class)).isFalse();
        });
    }

    @Test
    void productionWithoutDatabaseUrlFailsAtStartup() {
        productionWithout("DB_URL").run(context -> assertFailure(context.getStartupFailure(), "DB_URL"));
    }

    @Test
    void productionWithoutDatabaseUsernameFailsAtStartup() {
        productionWithout("DB_USERNAME").run(context -> assertFailure(context.getStartupFailure(), "DB_USERNAME"));
    }

    @Test
    void productionWithoutDatabasePasswordFailsAtStartup() {
        productionWithout("DB_PASSWORD").run(context -> assertFailure(context.getStartupFailure(), "DB_PASSWORD"));
    }

    @Test
    void productionWithEmptyDatabasePasswordFailsAtStartup() {
        productionWithout("DB_PASSWORD")
                .withPropertyValues("DB_PASSWORD=")
                .run(context -> assertFailure(context.getStartupFailure(), "DB_PASSWORD"));
    }

    @Test
    void productionWithWhitespaceDatabasePasswordFailsAtStartup() {
        productionWithout("DB_PASSWORD")
                .withPropertyValues("DB_PASSWORD=   ")
                .run(context -> assertFailure(context.getStartupFailure(), "DB_PASSWORD"));
    }

    @Test
    void productionWithoutTimeZoneFailsAtStartup() {
        productionWithout("APP_TIME_ZONE").run(context -> assertFailure(context.getStartupFailure(), "APP_TIME_ZONE"));
    }

    @Test
    void invalidTimeZoneFailsWithoutLeakingDatabasePassword() {
        productionWithout("APP_TIME_ZONE")
                .withPropertyValues("APP_TIME_ZONE=Invalid/Time_Zone")
                .run(context -> {
                    String messages = failureMessages(context.getStartupFailure());
                    assertThat(messages).contains("APP_TIME_ZONE").doesNotContain(SECRET);
                });
    }

    @Test
    void productionAndDevelopmentProfilesAreRejectedTogether() {
        validProduction(contextRunner)
                .withPropertyValues("spring.profiles.active=prod,dev")
                .run(context -> assertFailure(context.getStartupFailure(), "prod e dev"));
    }

    @Test
    void productionAndDemoProfilesAreRejectedTogetherBeforeDemoCanActivate() {
        validProduction(contextRunner)
                .withPropertyValues("spring.profiles.active=prod,demo")
                .run(context -> assertFailure(context.getStartupFailure(), "prod e demo"));
    }

    @Test
    void explicitDevelopmentProfileKeepsLocalConveniences() {
        contextRunner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://localhost:5432/agendafacil_pro");
            assertThat(context.getEnvironment().getProperty("app.time-zone")).isEqualTo("America/Sao_Paulo");
            assertThat(context.getEnvironment().getProperty("spring.thymeleaf.cache", Boolean.class)).isFalse();
            assertThat(context.getEnvironment().getProperty("server.servlet.session.cookie.secure", Boolean.class)).isFalse();
        });
    }

    @Test
    void noProfileDoesNotImplicitlySelectDevelopmentConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).isNull();
            assertThat(context.getEnvironment().getProperty("app.time-zone")).isNull();
            assertThat(context.getEnvironment().getProperty("spring.thymeleaf.cache", Boolean.class)).isTrue();
            assertThat(context.getEnvironment().getProperty("server.servlet.session.cookie.secure", Boolean.class)).isTrue();
        });
    }

    @Test
    void productionTimeZoneStillCreatesTheConfiguredZoneId() {
        validProduction(contextRunner.withUserConfiguration(TimeConfig.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ZoneId.class)).isEqualTo(ZoneId.of("America/Sao_Paulo"));
        });
    }

    private ApplicationContextRunner productionWithout(String property) {
        ApplicationContextRunner runner = contextRunner.withPropertyValues("spring.profiles.active=prod");
        if (!"DB_URL".equals(property)) {
            runner = runner.withPropertyValues("DB_URL=jdbc:postgresql://database.internal:5432/agendafacil");
        }
        if (!"DB_USERNAME".equals(property)) {
            runner = runner.withPropertyValues("DB_USERNAME=agenda_prod");
        }
        if (!"DB_PASSWORD".equals(property)) {
            runner = runner.withPropertyValues("DB_PASSWORD=" + SECRET);
        }
        if (!"APP_TIME_ZONE".equals(property)) {
            runner = runner.withPropertyValues("APP_TIME_ZONE=America/Sao_Paulo");
        }
        return runner;
    }

    private ApplicationContextRunner validProduction(ApplicationContextRunner runner) {
        return runner.withPropertyValues(
                "spring.profiles.active=prod",
                "DB_URL=jdbc:postgresql://database.internal:5432/agendafacil",
                "DB_USERNAME=agenda_prod",
                "DB_PASSWORD=" + SECRET,
                "APP_TIME_ZONE=America/Sao_Paulo");
    }

    private void assertFailure(Throwable failure, String expectedMessage) {
        assertThat(failure).isNotNull();
        assertThat(failureMessages(failure)).contains(expectedMessage).doesNotContain(SECRET);
    }

    private String failureMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            messages.append(current.getClass().getName()).append(':').append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return messages.toString();
    }
}
