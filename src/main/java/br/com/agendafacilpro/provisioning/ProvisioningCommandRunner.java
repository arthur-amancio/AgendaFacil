package br.com.agendafacilpro.provisioning;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

@Component
@Profile("prod & provisioning")
public class ProvisioningCommandRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ProvisioningCommandRunner.class);

    private final ProvisioningProperties properties;
    private final TenantProvisioningService service;
    private final InitialCredentialGenerator credentialGenerator;
    private final CredentialPublisher credentialPublisher;
    private final ProvisioningExecution execution;
    private final ApplicationContext applicationContext;
    private final Environment environment;
    private final Clock clock;

    public ProvisioningCommandRunner(
            ProvisioningProperties properties,
            TenantProvisioningService service,
            InitialCredentialGenerator credentialGenerator,
            CredentialPublisher credentialPublisher,
            ProvisioningExecution execution,
            ApplicationContext applicationContext,
            Environment environment,
            Clock clock) {
        this.properties = properties;
        this.service = service;
        this.credentialGenerator = credentialGenerator;
        this.credentialPublisher = credentialPublisher;
        this.execution = execution;
        this.applicationContext = applicationContext;
        this.environment = environment;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.acceptsProfiles(Profiles.of("prod & provisioning"))) {
            execution.failure("Provisioning recusado: os profiles prod e provisioning são obrigatórios.");
            return;
        }
        if (!properties.isEnabled()) {
            execution.failure("Provisioning desabilitado. Ative explicitamente app.provisioning.enabled=true.");
            return;
        }
        if (applicationContext instanceof WebApplicationContext) {
            execution.failure("Provisioning recusado: execute o comando em modo non-web.");
            return;
        }
        String command = properties.getCommand() == null ? "" : properties.getCommand().trim();
        try {
            switch (command) {
                case "create-tenant" -> createTenant();
                case "activate-tenant" -> activateTenant();
                default -> execution.failure("Comando inválido. Use create-tenant ou activate-tenant explicitamente.");
            }
        } catch (ProvisioningException ex) {
            execution.failure(ex.getMessage());
            log.warn("event=TENANT_PROVISIONING_FAILED command={} timestamp={}", safeCommand(command), Instant.now(clock));
        } catch (DataIntegrityViolationException ex) {
            execution.failure("A operação conflitou com dados existentes. Nenhum dado parcial foi mantido.");
            log.warn("event=TENANT_PROVISIONING_FAILED command={} reason=data_conflict timestamp={}", safeCommand(command), Instant.now(clock));
        } catch (RuntimeException ex) {
            execution.failure("O provisioning falhou de forma segura. Nenhum detalhe interno foi exposto.");
            log.error("event=TENANT_PROVISIONING_FAILED command={} reason=unexpected timestamp={}", safeCommand(command), Instant.now(clock));
        }
    }

    private void createTenant() {
        requireConfirmedSlug();
        CredentialPublisher.CredentialChannel channel = credentialPublisher.prepare(properties.getPasswordOutputFile());
        char[] credential = credentialGenerator.generate();
        try {
            TenantProvisioningResult result = service.create(new TenantProvisioningRequest(
                    properties.getName(),
                    properties.getSlug(),
                    properties.getWhatsapp(),
                    properties.getCity(),
                    properties.getDescription(),
                    properties.getOwnerName(),
                    properties.getOwnerEmail()), credential);

            log.info("event=TENANT_PROVISIONED tenantId={} slug={} ownerUserId={} timestamp={}",
                    result.tenantId(), result.slug(), result.ownerUserId(), Instant.now(clock));
            channel.publish(credential);
            execution.success("Tenant criado com sucesso e mantido inativo: " + result.slug());
        } finally {
            Arrays.fill(credential, '\0');
        }
    }

    private void activateTenant() {
        requireConfirmedSlug();
        TenantActivationResult result = service.activate(properties.getSlug());
        log.info("event=TENANT_ACTIVATED tenantId={} slug={} timestamp={}",
                result.tenantId(), result.slug(), Instant.now(clock));
        execution.success("Tenant ativado com sucesso: " + result.slug());
    }

    private void requireConfirmedSlug() {
        String slug = properties.getSlug();
        String confirmation = properties.getConfirmSlug();
        if (slug == null || confirmation == null || !slug.equals(confirmation)) {
            throw new ProvisioningException("Confirmação recusada: app.provisioning.confirm-slug deve ser idêntico ao slug informado.");
        }
    }

    private String safeCommand(String command) {
        return switch (command) {
            case "create-tenant", "activate-tenant" -> command;
            default -> "invalid";
        };
    }
}
