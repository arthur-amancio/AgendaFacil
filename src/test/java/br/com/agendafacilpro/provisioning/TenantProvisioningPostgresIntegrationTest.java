package br.com.agendafacilpro.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.agendafacilpro.domain.AppUser;
import br.com.agendafacilpro.domain.Establishment;
import br.com.agendafacilpro.domain.EstablishmentBusinessHours;
import br.com.agendafacilpro.domain.Professional;
import br.com.agendafacilpro.domain.ServiceItem;
import br.com.agendafacilpro.domain.UserRole;
import br.com.agendafacilpro.repo.EstablishmentBusinessHoursRepo;
import br.com.agendafacilpro.repo.EstablishmentRepo;
import br.com.agendafacilpro.repo.EstablishmentSettingsRepo;
import br.com.agendafacilpro.repo.ProfessionalRepo;
import br.com.agendafacilpro.repo.ServiceItemRepo;
import br.com.agendafacilpro.repo.UserRepo;

@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
class TenantProvisioningPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired TenantProvisioningService provisioning;
    @Autowired EstablishmentRepo establishments;
    @Autowired EstablishmentSettingsRepo settings;
    @Autowired EstablishmentBusinessHoursRepo businessHours;
    @Autowired UserRepo users;
    @Autowired ServiceItemRepo services;
    @Autowired ProfessionalRepo professionals;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsCompleteInactiveTenantWithDefaultsClosedWeekAndOwner() {
        Fixture fixture = fixture("complete");
        char[] credential = credential();

        TenantProvisioningResult result = provisioning.create(fixture.request(), credential);

        Establishment tenant = establishments.findById(result.tenantId()).orElseThrow();
        AppUser owner = users.findByEmailIgnoreCase(fixture.email()).orElseThrow();
        List<EstablishmentBusinessHours> week = businessHours.findByEstablishmentIdOrderByDayOfWeek(tenant.getId());
        assertThat(tenant.isActive()).isFalse();
        assertThat(tenant.getWhatsapp()).startsWith("55").hasSize(13);
        assertThat(settings.findByEstablishmentId(tenant.getId())).isPresent();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM establishment_settings WHERE establishment_id=?", Integer.class, tenant.getId())).isEqualTo(1);
        assertThat(week).hasSize(7).extracting(EstablishmentBusinessHours::getDayOfWeek)
                .containsExactlyInAnyOrder(DayOfWeek.values());
        assertThat(week).allSatisfy(day -> {
            assertThat(day.isOpen()).isFalse();
            assertThat(day.getOpeningTime()).isNull();
            assertThat(day.getClosingTime()).isNull();
        });
        assertThat(owner.getEstablishment().getId()).isEqualTo(tenant.getId());
        assertThat(owner.getRole()).isEqualTo(UserRole.OWNER);
        assertThat(owner.isEnabled()).isTrue();
        assertThat(owner.getEmail()).isEqualTo(fixture.email());
        assertThat(passwordEncoder.matches(new String(credential), owner.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("admin123", owner.getPasswordHash())).isFalse();
        assertThat(owner.getPasswordHash()).doesNotContain(new String(credential));
    }

    @Test
    void rollsBackEveryRecordWhenAnIntermediateInsertFails() {
        Fixture fixture = fixture("rollback");
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fail_p0_5_business_hours() RETURNS trigger AS $$
                BEGIN
                  IF EXISTS (SELECT 1 FROM establishments WHERE id=NEW.establishment_id AND slug LIKE 'rollback-%') THEN
                    RAISE EXCEPTION 'forced integration-test failure';
                  END IF;
                  RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("CREATE TRIGGER trg_fail_p0_5_business_hours BEFORE INSERT ON establishment_business_hours FOR EACH ROW EXECUTE FUNCTION fail_p0_5_business_hours()");
        try {
            assertThatThrownBy(() -> provisioning.create(fixture.request(), credential()))
                    .isInstanceOf(RuntimeException.class);

            assertThat(establishments.findBySlug(fixture.slug())).isEmpty();
            assertThat(users.findByEmailIgnoreCase(fixture.email())).isEmpty();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM establishment_settings s JOIN establishments e ON e.id=s.establishment_id WHERE e.slug=?", Integer.class, fixture.slug())).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM establishment_business_hours h JOIN establishments e ON e.id=h.establishment_id WHERE e.slug=?", Integer.class, fixture.slug())).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS trg_fail_p0_5_business_hours ON establishment_business_hours");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_p0_5_business_hours()");
        }
    }

    @Test
    void duplicateSlugEmailAndRepeatedExecutionFailWithoutUpdates() {
        Fixture existing = fixture("duplicates");
        provisioning.create(existing.request(), credential());

        Fixture sameSlug = fixture("other-email").withSlug(existing.slug());
        Fixture sameEmail = fixture("same-email").withEmail(existing.email());
        Fixture sameEmailDifferentCase = fixture("other-slug").withEmail(existing.email().toUpperCase());
        assertThatThrownBy(() -> provisioning.create(sameSlug.request(), credential()))
                .isInstanceOf(ProvisioningException.class).hasMessageContaining("slug");
        assertThatThrownBy(() -> provisioning.create(sameEmail.request(), credential()))
                .isInstanceOf(ProvisioningException.class).hasMessageContaining("e-mail");
        assertThatThrownBy(() -> provisioning.create(sameEmailDifferentCase.request(), credential()))
                .isInstanceOf(ProvisioningException.class).hasMessageContaining("e-mail");
        assertThatThrownBy(() -> provisioning.create(existing.request(), credential()))
                .isInstanceOf(ProvisioningException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM establishments WHERE slug=?", Integer.class, existing.slug())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users_app WHERE lower(email)=lower(?)", Integer.class, existing.email())).isEqualTo(1);
    }

    @Test
    void concurrentProvisioningHasExactlyOneWinnerAndNoPartialLoser() throws Exception {
        Fixture fixture = fixture("concurrent");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> task = () -> {
                try {
                    provisioning.create(fixture.request(), credential());
                    return true;
                } catch (RuntimeException ex) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(task);
            Future<Boolean> second = executor.submit(task);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);

            Long tenantId = establishments.findBySlug(fixture.slug()).orElseThrow().getId();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM establishments WHERE slug=?", Integer.class, fixture.slug())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users_app WHERE establishment_id=?", Integer.class, tenantId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM establishment_settings WHERE establishment_id=?", Integer.class, tenantId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM establishment_business_hours WHERE establishment_id=?", Integer.class, tenantId)).isEqualTo(7);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void allowsSameNameAndKeepsTwoTenantsIsolated() {
        Fixture first = fixture("isolation-a").withName("Mesmo Nome");
        Fixture second = fixture("isolation-b").withName("Mesmo Nome");
        TenantProvisioningResult firstResult = provisioning.create(first.request(), credential());
        TenantProvisioningResult secondResult = provisioning.create(second.request(), credential());

        assertThat(firstResult.tenantId()).isNotEqualTo(secondResult.tenantId());
        assertThat(users.findByEmailIgnoreCase(first.email()).orElseThrow().getEstablishment().getId()).isEqualTo(firstResult.tenantId());
        assertThat(users.findByEmailIgnoreCase(second.email()).orElseThrow().getEstablishment().getId()).isEqualTo(secondResult.tenantId());
        assertThat(businessHours.findByEstablishmentIdOrderByDayOfWeek(firstResult.tenantId())).hasSize(7);
        assertThat(businessHours.findByEstablishmentIdOrderByDayOfWeek(secondResult.tenantId())).hasSize(7);
    }

    @Test
    void activationRejectsClosedWeekAndMissingSettingsOrEnabledOwner() {
        Fixture closed = fixture("closed");
        provisioning.create(closed.request(), credential());
        assertThatThrownBy(() -> provisioning.activate(closed.slug())).hasMessageContaining("dia aberto");

        Fixture withoutSettings = fixture("no-settings");
        TenantProvisioningResult settingsResult = provisioning.create(withoutSettings.request(), credential());
        settings.delete(settings.findByEstablishmentId(settingsResult.tenantId()).orElseThrow());
        assertThatThrownBy(() -> provisioning.activate(withoutSettings.slug())).hasMessageContaining("configurações");

        Fixture withoutOwner = fixture("no-owner");
        provisioning.create(withoutOwner.request(), credential());
        openMonday(withoutOwner.slug());
        AppUser owner = users.findByEmailIgnoreCase(withoutOwner.email()).orElseThrow();
        owner.setEnabled(false);
        users.saveAndFlush(owner);
        assertThatThrownBy(() -> provisioning.activate(withoutOwner.slug())).hasMessageContaining("OWNER");
    }

    @Test
    void activationRejectsMissingCatalogPartsAndRelationship() {
        Fixture noService = fixture("no-service");
        provisioning.create(noService.request(), credential());
        openMonday(noService.slug());
        assertThatThrownBy(() -> provisioning.activate(noService.slug())).hasMessageContaining("serviço ativo");

        Fixture noProfessional = fixture("no-professional");
        provisioning.create(noProfessional.request(), credential());
        openMonday(noProfessional.slug());
        createService(tenant(noProfessional.slug()));
        assertThatThrownBy(() -> provisioning.activate(noProfessional.slug())).hasMessageContaining("profissional ativo");

        Fixture noRelationship = fixture("no-relationship");
        provisioning.create(noRelationship.request(), credential());
        openMonday(noRelationship.slug());
        Establishment tenant = tenant(noRelationship.slug());
        createService(tenant);
        createProfessional(tenant, null);
        assertThatThrownBy(() -> provisioning.activate(noRelationship.slug())).hasMessageContaining("vincule");
    }

    @Test
    void successfulActivationChangesOnlyExplicitTenant() {
        Fixture target = fixture("activate-target");
        Fixture other = fixture("activate-other");
        provisioning.create(target.request(), credential());
        provisioning.create(other.request(), credential());
        configureReadyTenant(target.slug());
        configureReadyTenant(other.slug());

        TenantActivationResult result = provisioning.activate(target.slug());

        assertThat(result.slug()).isEqualTo(target.slug());
        assertThat(tenant(target.slug()).isActive()).isTrue();
        assertThat(tenant(other.slug()).isActive()).isFalse();
        assertThatThrownBy(() -> provisioning.activate(target.slug())).hasMessageContaining("já está ativo");
    }

    private void configureReadyTenant(String slug) {
        Establishment tenant = tenant(slug);
        openMonday(slug);
        ServiceItem service = createService(tenant);
        createProfessional(tenant, service);
    }

    private void openMonday(String slug) {
        Establishment tenant = tenant(slug);
        EstablishmentBusinessHours monday = businessHours.findByEstablishmentIdAndDayOfWeek(tenant.getId(), DayOfWeek.MONDAY).orElseThrow();
        monday.setOpen(true);
        monday.setOpeningTime(LocalTime.of(8, 0));
        monday.setClosingTime(LocalTime.of(18, 0));
        businessHours.saveAndFlush(monday);
    }

    private ServiceItem createService(Establishment tenant) {
        ServiceItem service = new ServiceItem();
        service.setEstablishment(tenant);
        service.setName("Serviço ativo");
        service.setDurationMinutes(30);
        service.setPrice(BigDecimal.TEN);
        service.setActive(true);
        return services.saveAndFlush(service);
    }

    private Professional createProfessional(Establishment tenant, ServiceItem service) {
        Professional professional = new Professional();
        professional.setEstablishment(tenant);
        professional.setName("Profissional ativo");
        professional.setActive(true);
        if (service != null) professional.getServices().add(service);
        return professionals.saveAndFlush(professional);
    }

    private Establishment tenant(String slug) {
        return establishments.findBySlug(slug).orElseThrow();
    }

    private Fixture fixture(String prefix) {
        int suffix = SEQUENCE.incrementAndGet();
        String key = prefix + "-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return new Fixture("Estabelecimento " + suffix, key, "owner-" + key + "@example.test");
    }

    private char[] credential() {
        return ("Test-only-credential-" + UUID.randomUUID()).toCharArray();
    }

    private record Fixture(String name, String slug, String email) {
        Fixture withSlug(String value) { return new Fixture(name, value, email); }
        Fixture withEmail(String value) { return new Fixture(name, slug, value); }
        Fixture withName(String value) { return new Fixture(value, slug, email); }
        TenantProvisioningRequest request() {
            return new TenantProvisioningRequest(name, slug, "(17) 99999-9999", "Fernandópolis", "Piloto", "Owner Piloto", email);
        }
    }
}
