package br.com.agendafacilpro.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.agendafacilpro.domain.AppUser;
import br.com.agendafacilpro.domain.Establishment;
import br.com.agendafacilpro.repo.EstablishmentRepo;
import br.com.agendafacilpro.repo.UserRepo;

@Component
@Profile("demo & !prod")
public class DemoDataActivator implements ApplicationRunner {

    static final String DEMO_SLUG = "agenda-demo";
    static final String DEMO_EMAIL = "admin@demo.local";
    static final String DEMO_PASSWORD_HASH = "$2y$10$SSqTDKeVQzAepUbciVQu0.XRLXRUm2BXy7FY.sZtqoo2l1fLKC3tK";

    private final EstablishmentRepo establishments;
    private final UserRepo users;

    public DemoDataActivator(EstablishmentRepo establishments, UserRepo users) {
        this.establishments = establishments;
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Establishment establishment = establishments.findBySlug(DEMO_SLUG)
                .orElseThrow(() -> new IllegalStateException("Dados de demonstracao nao encontrados."));
        AppUser user = users.findByEmailIgnoreCase(DEMO_EMAIL)
                .orElseThrow(() -> new IllegalStateException("Usuario de demonstracao nao encontrado."));

        if (!establishment.getId().equals(user.getEstablishment().getId())) {
            throw new IllegalStateException("Usuario de demonstracao pertence a outro estabelecimento.");
        }

        establishment.setActive(true);
        user.setPasswordHash(DEMO_PASSWORD_HASH);
        user.setEnabled(true);
        establishments.save(establishment);
        users.save(user);
    }
}
