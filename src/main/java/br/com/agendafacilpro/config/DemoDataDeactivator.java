package br.com.agendafacilpro.config;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.agendafacilpro.domain.AppUser;
import br.com.agendafacilpro.repo.EstablishmentRepo;
import br.com.agendafacilpro.repo.UserRepo;

@Component
@Profile("!demo | prod")
public class DemoDataDeactivator implements ApplicationRunner {

    static final String DISABLED_PASSWORD_HASH = "!disabled-demo-account!";

    private final EstablishmentRepo establishments;
    private final UserRepo users;

    public DemoDataDeactivator(EstablishmentRepo establishments, UserRepo users) {
        this.establishments = establishments;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<AppUser> demoUsers = new LinkedHashSet<>();

        establishments.findBySlug(DemoDataActivator.DEMO_SLUG).ifPresent(establishment -> {
            demoUsers.addAll(users.findAllByEstablishmentId(establishment.getId()));
            establishment.setActive(false);
            establishments.save(establishment);
        });
        users.findByEmailIgnoreCase(DemoDataActivator.DEMO_EMAIL).ifPresent(demoUsers::add);
        demoUsers.addAll(users.findAllByPasswordHash(DemoDataActivator.DEMO_PASSWORD_HASH));

        demoUsers.forEach(user -> {
            user.setEnabled(false);
            user.setPasswordHash(DISABLED_PASSWORD_HASH);
            users.save(user);
        });
    }
}
