package br.com.agendafacilpro.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Profiles;
import org.springframework.mock.env.MockEnvironment;

import br.com.agendafacilpro.domain.AppUser;
import br.com.agendafacilpro.domain.Establishment;
import br.com.agendafacilpro.repo.EstablishmentRepo;
import br.com.agendafacilpro.repo.UserRepo;

class DemoDataActivatorTest {

    private final EstablishmentRepo establishments = mock(EstablishmentRepo.class);
    private final UserRepo users = mock(UserRepo.class);
    private final DemoDataActivator activator = new DemoDataActivator(establishments, users);

    @Test
    void activatesOnlyTheExpectedDemoTenantAndRestoresItsLocalPassword() throws Exception {
        Establishment establishment = establishment(1L, false);
        AppUser user = user(10L, establishment, false, DemoDataDeactivator.DISABLED_PASSWORD_HASH);
        when(establishments.findBySlug(DemoDataActivator.DEMO_SLUG)).thenReturn(Optional.of(establishment));
        when(users.findByEmailIgnoreCase(DemoDataActivator.DEMO_EMAIL)).thenReturn(Optional.of(user));

        activator.run(null);

        assertThat(establishment.isActive()).isTrue();
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo(DemoDataActivator.DEMO_PASSWORD_HASH);
        verify(establishments).save(establishment);
        verify(users).save(user);
    }

    @Test
    void refusesToActivateAUserFromAnotherTenant() {
        Establishment demo = establishment(1L, false);
        AppUser user = user(10L, establishment(2L, true), false, DemoDataDeactivator.DISABLED_PASSWORD_HASH);
        when(establishments.findBySlug(DemoDataActivator.DEMO_SLUG)).thenReturn(Optional.of(demo));
        when(users.findByEmailIgnoreCase(DemoDataActivator.DEMO_EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> activator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Usuario de demonstracao pertence a outro estabelecimento.");

        assertThat(demo.isActive()).isFalse();
        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    void profileRequiresDevAndDemoWithoutProduction() {
        Profile profile = DemoDataActivator.class.getAnnotation(Profile.class);
        Profiles expression = Profiles.of(profile.value());

        assertThat(new MockEnvironment().withProperty("spring.profiles.active", "dev").acceptsProfiles(expression)).isFalse();
        assertThat(new MockEnvironment().withProperty("spring.profiles.active", "demo").acceptsProfiles(expression)).isFalse();
        assertThat(new MockEnvironment().withProperty("spring.profiles.active", "dev,demo").acceptsProfiles(expression)).isTrue();
        assertThat(new MockEnvironment().withProperty("spring.profiles.active", "staging,demo").acceptsProfiles(expression)).isFalse();
        assertThat(new MockEnvironment().withProperty("spring.profiles.active", "prod").acceptsProfiles(expression)).isFalse();
        assertThat(new MockEnvironment().withProperty("spring.profiles.active", "demo,prod").acceptsProfiles(expression)).isFalse();
        assertThat(new MockEnvironment().withProperty("spring.profiles.active", "dev,demo,prod").acceptsProfiles(expression)).isFalse();
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
