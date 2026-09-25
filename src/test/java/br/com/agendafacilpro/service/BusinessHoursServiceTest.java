package br.com.agendafacilpro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.*;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.agendafacilpro.domain.Establishment;
import br.com.agendafacilpro.domain.EstablishmentBusinessHours;
import br.com.agendafacilpro.repo.EstablishmentBusinessHoursRepo;

class BusinessHoursServiceTest {
    private final EstablishmentBusinessHoursRepo repository = mock(EstablishmentBusinessHoursRepo.class);
    private final BusinessHoursService service = new BusinessHoursService(repository);

    @Test
    void openDayAcceptsOpeningAndServiceEndingAtClosing() {
        EstablishmentBusinessHours hours = open(DayOfWeek.MONDAY, "08:00", "18:00");
        assertThat(service.fits(hours, at("08:00"), at("09:00"))).isTrue();
        assertThat(service.fits(hours, at("17:00"), at("18:00"))).isTrue();
    }

    @Test
    void rejectsBeforeOpeningAndAfterClosing() {
        EstablishmentBusinessHours hours = open(DayOfWeek.MONDAY, "08:00", "18:00");
        assertThat(service.fits(hours, at("07:30"), at("08:30"))).isFalse();
        assertThat(service.fits(hours, at("17:30"), at("18:30"))).isFalse();
    }

    @Test
    void closedOrMissingDayIsFailClosed() {
        EstablishmentBusinessHours closed = open(DayOfWeek.SUNDAY, "08:00", "18:00");
        closed.setOpen(false);
        closed.setOpeningTime(null);
        closed.setClosingTime(null);
        assertThat(service.starts(closed)).isEmpty();
        assertThat(service.starts(null)).isEmpty();
        assertThat(service.fits(closed, at("08:00"), at("09:00"))).isFalse();
    }

    @Test
    void gridIsAnchoredAtOpeningTime() {
        EstablishmentBusinessHours hours = open(DayOfWeek.MONDAY, "08:15", "10:00");
        assertThat(service.starts(hours)).containsExactly(
                LocalTime.of(8, 15), LocalTime.of(8, 45), LocalTime.of(9, 15), LocalTime.of(9, 45));
        assertThat(service.fits(hours, at("08:45"), at("09:45"))).isTrue();
        assertThat(service.fits(hours, at("09:00"), at("09:30"))).isFalse();
    }

    @Test
    void loadsHoursUsingTenantAndDay() {
        LocalDate monday = LocalDate.of(2030, 1, 7);
        EstablishmentBusinessHours expected = open(DayOfWeek.MONDAY, "09:00", "17:00");
        when(repository.findByEstablishmentIdAndDayOfWeek(22L, DayOfWeek.MONDAY)).thenReturn(Optional.of(expected));
        assertThat(service.hours(22L, monday)).hasValueSatisfying(value -> assertThat(value).isSameAs(expected));
    }

    @Test
    void updateRequiresAllSevenDaysAndValidOpenInterval() {
        Establishment establishment = new Establishment();
        establishment.setId(1L);
        assertThatThrownBy(() -> service.update(establishment, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sete dias");
    }

    @Test
    void saoPauloClockUsesPreviousLocalDateBeforeThreeUtc() {
        Clock clock = Clock.fixed(Instant.parse("2030-01-08T02:30:00Z"), ZoneId.of("America/Sao_Paulo"));
        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2030, 1, 7));
        assertThat(LocalTime.now(clock)).isEqualTo(LocalTime.of(23, 30));
    }

    private EstablishmentBusinessHours open(DayOfWeek day, String opening, String closing) {
        EstablishmentBusinessHours hours = new EstablishmentBusinessHours();
        hours.setDayOfWeek(day);
        hours.setOpen(true);
        hours.setOpeningTime(LocalTime.parse(opening));
        hours.setClosingTime(LocalTime.parse(closing));
        return hours;
    }

    private LocalDateTime at(String time) {
        return LocalDate.of(2030, 1, 7).atTime(LocalTime.parse(time));
    }
}
