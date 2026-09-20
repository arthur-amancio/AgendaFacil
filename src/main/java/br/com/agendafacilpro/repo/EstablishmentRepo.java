package br.com.agendafacilpro.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.agendafacilpro.domain.Establishment;

public interface EstablishmentRepo extends JpaRepository<Establishment, Long> {

    Optional<Establishment> findBySlug(String slug);

    Optional<Establishment> findBySlugAndActiveTrue(String slug);
}
