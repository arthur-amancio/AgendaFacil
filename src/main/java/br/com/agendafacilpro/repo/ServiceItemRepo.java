package br.com.agendafacilpro.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.agendafacilpro.domain.ServiceItem;

public interface ServiceItemRepo extends JpaRepository<ServiceItem, Long> {

    List<ServiceItem> findByEstablishmentIdAndActiveTrueOrderBySortOrderAscNameAsc(Long establishmentId);

    List<ServiceItem> findByEstablishmentIdOrderByActiveDescSortOrderAscNameAsc(Long establishmentId);

    Optional<ServiceItem> findByIdAndEstablishmentId(Long id, Long establishmentId);

    boolean existsByEstablishmentIdAndActiveTrue(Long establishmentId);
}
