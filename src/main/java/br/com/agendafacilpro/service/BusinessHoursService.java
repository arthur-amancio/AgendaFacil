package br.com.agendafacilpro.service;

import java.time.*;
import java.time.format.TextStyle;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agendafacilpro.domain.Establishment;
import br.com.agendafacilpro.domain.EstablishmentBusinessHours;
import br.com.agendafacilpro.repo.EstablishmentBusinessHoursRepo;

@Service
public class BusinessHoursService {
    public static final int SLOT_STEP_MINUTES = 30;
    private final EstablishmentBusinessHoursRepo repository;

    public BusinessHoursService(EstablishmentBusinessHoursRepo repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<EstablishmentBusinessHours> hours(Long establishmentId, LocalDate date) {
        return repository.findByEstablishmentIdAndDayOfWeek(establishmentId, date.getDayOfWeek());
    }

    @Transactional(readOnly = true)
    public List<EstablishmentBusinessHours> weekly(Long establishmentId) {
        Map<DayOfWeek, EstablishmentBusinessHours> indexed = new EnumMap<>(DayOfWeek.class);
        repository.findByEstablishmentIdOrderByDayOfWeek(establishmentId).forEach(h -> indexed.put(h.getDayOfWeek(), h));
        return Arrays.stream(DayOfWeek.values()).map(indexed::get).filter(Objects::nonNull).toList();
    }

    public boolean fits(EstablishmentBusinessHours hours, LocalDateTime start, LocalDateTime end) {
        if (hours == null || !hours.isOpen() || hours.getOpeningTime() == null || hours.getClosingTime() == null) return false;
        if (!start.toLocalDate().equals(end.toLocalDate()) || start.getSecond() != 0 || start.getNano() != 0) return false;
        long offset = Duration.between(hours.getOpeningTime(), start.toLocalTime()).toMinutes();
        return offset >= 0 && offset % SLOT_STEP_MINUTES == 0 && !end.toLocalTime().isAfter(hours.getClosingTime());
    }

    public List<LocalTime> starts(EstablishmentBusinessHours hours) {
        if (hours == null || !hours.isOpen()) return List.of();
        List<LocalTime> starts = new ArrayList<>();
        long openMinutes = Duration.between(hours.getOpeningTime(), hours.getClosingTime()).toMinutes();
        for (long offset = 0; offset < openMinutes; offset += SLOT_STEP_MINUTES) {
            starts.add(hours.getOpeningTime().plusMinutes(offset));
        }
        return starts;
    }

    @Transactional
    public void update(Establishment establishment, List<BusinessHoursForm> forms) {
        if (forms == null || forms.size() != 7 || forms.stream().map(BusinessHoursForm::dayOfWeek).filter(Objects::nonNull).distinct().count() != 7) {
            throw new IllegalArgumentException("Informe os horários dos sete dias da semana.");
        }
        for (BusinessHoursForm form : forms) {
            EstablishmentBusinessHours hours = repository.findByEstablishmentIdAndDayOfWeek(establishment.getId(), form.dayOfWeek())
                    .orElseThrow(() -> new IllegalStateException("Configuração semanal incompleta. Fale com o suporte."));
            apply(hours, form);
        }
    }

    private void apply(EstablishmentBusinessHours hours, BusinessHoursForm form) {
        hours.setOpen(form.open());
        if (!form.open()) {
            hours.setOpeningTime(null);
            hours.setClosingTime(null);
            return;
        }
        if (form.openingTime() == null || form.closingTime() == null || !form.openingTime().isBefore(form.closingTime())) {
            String day = form.dayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pt-BR"));
            throw new IllegalArgumentException("Informe abertura e fechamento válidos para " + day + ".");
        }
        hours.setOpeningTime(form.openingTime().withSecond(0).withNano(0));
        hours.setClosingTime(form.closingTime().withSecond(0).withNano(0));
    }
}
