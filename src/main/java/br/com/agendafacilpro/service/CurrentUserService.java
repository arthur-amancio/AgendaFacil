package br.com.agendafacilpro.service;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agendafacilpro.domain.AppUser;
import br.com.agendafacilpro.repo.UserRepo;

@Service
public class CurrentUserService {

    private final UserRepo users;

    public CurrentUserService(UserRepo users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public AppUser user() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new StateConflictException("Sessão expirada. Faça login novamente."));
    }

    @Transactional(readOnly = true)
    public Long establishmentId() {
        return user().getEstablishment().getId();
    }
}
