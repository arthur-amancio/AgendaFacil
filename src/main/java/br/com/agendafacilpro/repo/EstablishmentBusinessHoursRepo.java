package br.com.agendafacilpro.repo;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.agendafacilpro.domain.EstablishmentBusinessHours;

public interface EstablishmentBusinessHoursRepo extends JpaRepository<EstablishmentBusinessHours, Long> {
    Optional<EstablishmentBusinessHours> findByEstablishmentIdAndDayOfWeek(Long establishmentId, DayOfWeek dayOfWeek);
    List<EstablishmentBusinessHours> findByEstablishmentIdOrderByDayOfWeek(Long establishmentId);
}
