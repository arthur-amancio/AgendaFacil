package br.com.agendafacilpro.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.agendafacilpro.domain.EstablishmentSettings;

public interface EstablishmentSettingsRepo extends JpaRepository<EstablishmentSettings, Long> {

    Optional<EstablishmentSettings> findByEstablishmentId(Long establishmentId);

    boolean existsByEstablishmentId(Long establishmentId);
}
