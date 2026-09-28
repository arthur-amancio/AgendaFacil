package br.com.agendafacilpro.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class ProvisioningCommandRunnerTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(ProvisioningCommandRunner.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    ProvisioningCommandRunnerTest() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachLogger() {
        logger.detachAppender(logs);
    }

    @Test
    void profileRunnerStillRequiresEnabledFlag() throws Exception {
        Harness harness = harness();
        harness.properties.setCommand("create-tenant");

        harness.runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(harness.execution.exitCode()).isEqualTo(2);
        assertThat(harness.execution.message()).contains("desabilitado");
        verify(harness.service, never()).create(any(), any());
    }

    @Test
    void provisioningWithoutProductionProfileNeverReachesTheService() throws Exception {
        Harness harness = harness("provisioning");
        configureCreate(harness);

        harness.runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(harness.execution.exitCode()).isEqualTo(2);
        assertThat(harness.execution.message()).contains("prod e provisioning");
        verify(harness.service, never()).create(any(), any());
    }

    @Test
    void developmentProvisioningNeverReachesTheService() throws Exception {
        Harness harness = harness("dev,provisioning");
        configureCreate(harness);

        harness.runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(harness.execution.exitCode()).isEqualTo(2);
        assertThat(harness.execution.message()).contains("prod e provisioning");
        verify(harness.service, never()).create(any(), any());
    }

    @Test
    void enabledFlagStillRequiresExplicitCommand() throws Exception {
        Harness harness = harness();
        harness.properties.setEnabled(true);

        harness.runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(harness.execution.exitCode()).isEqualTo(2);
        assertThat(harness.execution.message()).contains("Comando inválido");
        verify(harness.service, never()).create(any(), any());
    }

    @Test
    void createRequiresMatchingSlugConfirmation() throws Exception {
        Harness harness = harness();
        configureCreate(harness);
        harness.properties.setConfirmSlug("outro-slug");

        harness.runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(harness.execution.exitCode()).isEqualTo(2);
        assertThat(harness.execution.message()).contains("Confirmação recusada");
        verify(harness.service, never()).create(any(), any());
    }

    @Test
    void successfulCreatePublishesOnceAfterServiceReturnsAndNeverLogsSecret() throws Exception {
        Harness harness = harness();
        configureCreate(harness);
        char[] secret = "runner-secret-that-must-never-be-logged".toCharArray();
        when(harness.generator.generate()).thenReturn(secret);
        when(harness.service.create(any(), eq(secret))).thenReturn(new TenantProvisioningResult(321L, "piloto-seguro", 654L));

        harness.runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(harness.execution.exitCode()).isZero();
        verify(harness.channel).publish(eq(secret));
        ArgumentCaptor<TenantProvisioningRequest> request = ArgumentCaptor.forClass(TenantProvisioningRequest.class);
        verify(harness.service).create(request.capture(), eq(secret));
        assertThat(request.getValue().ownerEmail()).isEqualTo("owner@example.test");
        assertThat(logMessages()).noneMatch(message -> message.contains("runner-secret") || message.contains("owner@example.test"));
        assertThat(secret).containsOnly('\0');
    }

    @Test
    void activateUsesOnlyExplicitSlugAndDoesNotGenerateCredential() throws Exception {
        Harness harness = harness();
        harness.properties.setEnabled(true);
        harness.properties.setCommand("activate-tenant");
        harness.properties.setSlug("piloto-seguro");
        harness.properties.setConfirmSlug("piloto-seguro");
        when(harness.service.activate("piloto-seguro")).thenReturn(new TenantActivationResult(321L, "piloto-seguro"));

        harness.runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(harness.execution.exitCode()).isZero();
        verify(harness.service).activate("piloto-seguro");
        verify(harness.generator, never()).generate();
    }

    private void configureCreate(Harness harness) {
        harness.properties.setEnabled(true);
        harness.properties.setCommand("create-tenant");
        harness.properties.setName("Piloto");
        harness.properties.setSlug("piloto-seguro");
        harness.properties.setConfirmSlug("piloto-seguro");
        harness.properties.setWhatsapp("17999999999");
        harness.properties.setOwnerName("Owner");
        harness.properties.setOwnerEmail("owner@example.test");
    }

    private Harness harness() {
        return harness("prod,provisioning");
    }

    private Harness harness(String activeProfiles) {
        ProvisioningProperties properties = new ProvisioningProperties();
        TenantProvisioningService service = mock(TenantProvisioningService.class);
        InitialCredentialGenerator generator = mock(InitialCredentialGenerator.class);
        CredentialPublisher publisher = mock(CredentialPublisher.class);
        CredentialPublisher.CredentialChannel channel = mock(CredentialPublisher.CredentialChannel.class);
        when(publisher.prepare(any())).thenReturn(channel);
        ProvisioningExecution execution = new ProvisioningExecution();
        ApplicationContext context = mock(ApplicationContext.class);
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", activeProfiles);
        Clock clock = Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneOffset.UTC);
        ProvisioningCommandRunner runner = new ProvisioningCommandRunner(
                properties, service, generator, publisher, execution, context, environment, clock);
        return new Harness(properties, service, generator, channel, execution, runner);
    }

    private List<String> logMessages() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private record Harness(
            ProvisioningProperties properties,
            TenantProvisioningService service,
            InitialCredentialGenerator generator,
            CredentialPublisher.CredentialChannel channel,
            ProvisioningExecution execution,
            ProvisioningCommandRunner runner) {
    }
}
