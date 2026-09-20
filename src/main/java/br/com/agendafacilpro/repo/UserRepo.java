package br.com.agendafacilpro.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.agendafacilpro.domain.AppUser;

public interface UserRepo extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    List<AppUser> findAllByEstablishmentId(Long establishmentId);

    List<AppUser> findAllByPasswordHash(String passwordHash);
}
