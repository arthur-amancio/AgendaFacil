package br.com.agendafacilpro.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Profiles;
import org.springframework.mock.env.MockEnvironment;

import br.com.agendafacilpro.domain.AppUser;
import br.com.agendafacilpro.domain.Establishment;
import br.com.agendafacilpro.repo.EstablishmentRepo;
import br.com.agendafacilpro.repo.UserRepo;

class DemoDataDeactivatorTest {

    private final EstablishmentRepo establishments = mock(EstablishmentRepo.class);
    private final UserRepo users = mock(UserRepo.class);
    private final DemoDataDeactivator deactivator = new DemoDataDeactivator(establishments, users);

    @Test
    void disablesTheTenantAndEveryRecognizableDemoCredential() throws Exception {
        Establishment establishment = establishment(1L, true);
        AppUser tenantUser = user(10L, establishment, true, "another-hash");
        AppUser knownEmail = user(11L, establishment(2L, true), true, "another-hash");
        AppUser knownHash = user(12L, establishment(3L, true), true, DemoDataActivator.DEMO_PASSWORD_HASH);
        when(establishments.findBySlug(DemoDataActivator.DEMO_SLUG)).thenReturn(Optional.of(establishment));
        when(users.findAllByEstablishmentId(1L)).thenReturn(List.of(tenantUser));
        when(users.findByEmailIgnoreCase(DemoDataActivator.DEMO_EMAIL)).thenReturn(Optional.of(knownEmail));
        when(users.findAllByPasswordHash(DemoDataActivator.DEMO_PASSWORD_HASH)).thenReturn(List.of(knownHash));

        deactivator.run(null);

        assertThat(establishment.isActive()).isFalse();
        assertThat(List.of(tenantUser, knownEmail, knownHash)).allSatisfy(user -> {
            assertThat(user.isEnabled()).isFalse();
            assertThat(user.getPasswordHash()).isEqualTo(DemoDataDeactivator.DISABLED_PASSWORD_HASH);
        });
        verify(establishments).save(establishment);
        verify(users).save(tenantUser);
        verify(users).save(knownEmail);
        verify(users).save(knownHash);
    }

    @Test
    void profileCoversEveryCombinationExceptDevAndDemoWithoutProduction() {
        Profile profile = DemoDataDeactivator.class.getAnnotation(Profile.class);
        Profiles expression = Profiles.of(profile.value());

        assertThat(new MockEnvironment().withProperty("spring.profiles.active", "dev").acceptsProfiles(expression)).isTrue();
        assertThat(new MockEnvironment().withProperty("spring.profiles.active", "demo").acceptsProfiles(expression)).isTrue();
        assertThat(new MockEnvironment().withProperty("spring.profiles.active", "dev,demo").acceptsProfiles(expression)).isFalse();
        assertThat(new MockEnvironment().withProperty("spring.profiles.active", "staging,demo").acceptsProfiles(expression)).isTrue();
        assertThat(new MockEnvironment().withProperty("spring.profiles.active", "prod").acceptsProfiles(expression)).isTrue();
        assertThat(new MockEnvironment().withProperty("spring.profiles.active", "demo,prod").acceptsProfiles(expression)).isTrue();
        assertThat(new MockEnvironment().withProperty("spring.profiles.active", "dev,demo,prod").acceptsProfiles(expression)).isTrue();
    }

    private Establishment establishment(Long id, boolean active) {
        Establishment establishment = new Establishment();
        establishment.setId(id);
        establishment.setActive(active);
        return establishment;
    }

    private AppUser user(Long id, Establishment establishment, boolean enabled, String passwordHash) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEstablishment(establishment);
        user.setEnabled(enabled);
        user.setPasswordHash(passwordHash);
        return user;
    }
}
