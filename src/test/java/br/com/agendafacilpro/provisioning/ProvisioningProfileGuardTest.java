package br.com.agendafacilpro.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import br.com.agendafacilpro.config.RuntimeEnvironmentConfiguration;

class ProvisioningProfileGuardTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    RuntimeEnvironmentConfiguration.class,
                    ProvisioningConfiguration.class,
                    ProvisioningCommandRunner.class,
                    ProvisioningExecution.class)
            .withBean(TenantProvisioningService.class, () -> mock(TenantProvisioningService.class))
            .withBean(InitialCredentialGenerator.class, () -> mock(InitialCredentialGenerator.class))
            .withBean(CredentialPublisher.class, () -> mock(CredentialPublisher.class))
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void normalStartupDoesNotCreateProvisioningRunner() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(ProvisioningCommandRunner.class));
    }

    @Test
    void enabledFlagWithoutProfilesDoesNotCreateProvisioningRunner() {
        contextRunner.withPropertyValues("app.provisioning.enabled=true", "app.provisioning.command=create-tenant")
                .run(context -> assertThat(context).doesNotHaveBean(ProvisioningCommandRunner.class));
    }

    @Test
    void provisioningWithoutProductionFailsTheContext() {
        withProfiles("provisioning").run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureMessages(context.getStartupFailure())).contains("provisioning exige o profile prod");
        });
    }

    @Test
    void developmentProvisioningFailsTheContext() {
        withProfiles("dev,provisioning").run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureMessages(context.getStartupFailure())).contains("provisioning exige o profile prod");
        });
    }

    @Test
    void productionWithoutProvisioningDoesNotCreateTheRunner() {
        validProduction(withProfiles("prod")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ProvisioningCommandRunner.class);
        });
    }

    @Test
    void productionProvisioningCreatesRunnerButKeepsItDisabledByDefault() {
        validProduction(withProfiles("prod,provisioning")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProvisioningCommandRunner.class);
            assertThat(context.getBean(ProvisioningProperties.class).isEnabled()).isFalse();

            context.getBean(ProvisioningCommandRunner.class)
                    .run(new DefaultApplicationArguments(new String[0]));

            assertThat(context.getBean(ProvisioningExecution.class).exitCode()).isEqualTo(2);
            verify(context.getBean(TenantProvisioningService.class), never()).activate("piloto");
        });
    }

    @Test
    void explicitProductionProvisioningCanReachAConfirmedOperation() {
        validProduction(withProfiles("prod,provisioning"))
                .withPropertyValues(
                        "app.provisioning.enabled=true",
                        "app.provisioning.command=activate-tenant",
                        "app.provisioning.slug=piloto",
                        "app.provisioning.confirm-slug=piloto")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TenantProvisioningService service = context.getBean(TenantProvisioningService.class);
                    when(service.activate("piloto")).thenReturn(new TenantActivationResult(1L, "piloto"));

                    context.getBean(ProvisioningCommandRunner.class)
                            .run(new DefaultApplicationArguments(new String[0]));

                    verify(service).activate("piloto");
                    assertThat(context.getBean(ProvisioningExecution.class).exitCode()).isZero();
                });
    }

    private ApplicationContextRunner withProfiles(String profiles) {
        return contextRunner.withInitializer(
                context -> context.getEnvironment().setActiveProfiles(profiles.split(",")));
    }

    private ApplicationContextRunner validProduction(ApplicationContextRunner runner) {
        return runner.withPropertyValues(
                "DB_URL=jdbc:postgresql://database.internal:5432/agendafacil",
                "DB_USERNAME=agenda_prod",
                "DB_PASSWORD=test-secret",
                "APP_TIME_ZONE=America/Sao_Paulo");
    }

    private String failureMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            messages.append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return messages.toString();
    }
}
