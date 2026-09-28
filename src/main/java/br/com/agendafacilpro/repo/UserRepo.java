package br.com.agendafacilpro.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.agendafacilpro.domain.AppUser;
import br.com.agendafacilpro.domain.UserRole;

public interface UserRepo extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEstablishmentIdAndRoleAndEnabledTrue(Long establishmentId, UserRole role);

    List<AppUser> findAllByEstablishmentId(Long establishmentId);

    List<AppUser> findAllByPasswordHash(String passwordHash);
}
