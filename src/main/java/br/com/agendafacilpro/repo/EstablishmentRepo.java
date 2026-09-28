package br.com.agendafacilpro.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.agendafacilpro.domain.Establishment;
import jakarta.persistence.LockModeType;

public interface EstablishmentRepo extends JpaRepository<Establishment, Long> {

    Optional<Establishment> findBySlug(String slug);

    Optional<Establishment> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Establishment e where e.slug=:slug")
    Optional<Establishment> findForUpdateBySlug(@Param("slug") String slug);
}
