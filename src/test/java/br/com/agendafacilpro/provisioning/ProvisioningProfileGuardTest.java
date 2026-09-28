package br.com.agendafacilpro.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProvisioningProfileGuardTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
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
    void enabledFlagWithoutProfileDoesNotCreateProvisioningRunner() {
        contextRunner.withPropertyValues("app.provisioning.enabled=true", "app.provisioning.command=create-tenant")
                .run(context -> assertThat(context).doesNotHaveBean(ProvisioningCommandRunner.class));
    }

    @Test
    void provisioningProfileCreatesGuardedRunnerButDoesNotEnableItByItself() {
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("provisioning"))
                .run(context -> {
                    assertThat(context).hasSingleBean(ProvisioningCommandRunner.class);
                    assertThat(context.getBean(ProvisioningProperties.class).isEnabled()).isFalse();
                });
    }

    @Test
    void profileFlagAndCommandBindOnlyWhenExplicitlyProvidedTogether() {
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("provisioning"))
                .withPropertyValues(
                        "app.provisioning.enabled=true",
                        "app.provisioning.command=activate-tenant",
                        "app.provisioning.slug=piloto",
                        "app.provisioning.confirm-slug=piloto")
                .run(context -> {
                    ProvisioningProperties properties = context.getBean(ProvisioningProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getCommand()).isEqualTo("activate-tenant");
                });
    }
}
