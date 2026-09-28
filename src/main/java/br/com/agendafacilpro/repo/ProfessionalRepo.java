package br.com.agendafacilpro.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.agendafacilpro.domain.Professional;

public interface ProfessionalRepo extends JpaRepository<Professional, Long> {

    @EntityGraph(attributePaths = "services")
    List<Professional> findByEstablishmentIdAndActiveTrueOrderBySortOrderAscNameAsc(Long establishmentId);

    @EntityGraph(attributePaths = "services")
    List<Professional> findByEstablishmentIdOrderByActiveDescSortOrderAscNameAsc(Long establishmentId);

    @EntityGraph(attributePaths = "services")
    Optional<Professional> findByIdAndEstablishmentId(Long id, Long establishmentId);

    @Query("""
    select distinct p from Professional p
    join p.services s
    where p.establishment.id=:establishmentId
      and p.active=true
      and s.id=:serviceId
      and s.active=true
    order by p.sortOrder asc, p.name asc
  """)
    List<Professional> findActiveQualified(@Param("establishmentId") Long establishmentId, @Param("serviceId") Long serviceId);

    @Query("""
    select count(p)>0 from Professional p
    join p.services s
    where p.establishment.id=:establishmentId
      and p.id=:professionalId
      and p.active=true
      and s.id=:serviceId
      and s.active=true
  """)
    boolean existsActiveQualified(@Param("establishmentId") Long establishmentId, @Param("professionalId") Long professionalId, @Param("serviceId") Long serviceId);

    boolean existsByEstablishmentIdAndActiveTrue(Long establishmentId);

    @Query("""
    select count(p)>0 from Professional p
    join p.services s
    where p.establishment.id=:establishmentId
      and s.establishment.id=:establishmentId
      and p.active=true
      and s.active=true
  """)
    boolean existsAnyActiveQualified(@Param("establishmentId") Long establishmentId);
}
