package br.com.agendafacilpro.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agendafacilpro.domain.Appointment;
import br.com.agendafacilpro.domain.AppointmentStatus;
import br.com.agendafacilpro.repo.AppointmentRepo;

@Service
public class DashboardService {

    private final AppointmentRepo appointments;
    private final AppointmentService appointmentService;
    private final Clock clock;

    public DashboardService(AppointmentRepo a, AppointmentService s, Clock clock) {
        appointments = a;
        appointmentService = s;
        this.clock = clock;
    }

    public record Metric(String label, long value, String hint) {

    }

    public record Day(LocalDate date, String label, List<Appointment> appointments) {

    }

    public record Filter(LocalDate startDate, LocalDate endDate, Long professionalId, AppointmentStatus status) {

    }

    public record Data(List<Metric> metrics, List<Appointment> pending, List<Day> week, List<Appointment> today, List<Appointment> agenda, List<Appointment> history) {

    }

    public record ReportMetric(String label, String value, String hint) {

    }

    public record Reports(List<ReportMetric> metrics, List<Appointment> recentHistory) {

    }

    @Transactional
    public Data data(Long est) {
        return data(est, new Filter(LocalDate.now(clock), LocalDate.now(clock), null, null));
    }

    @Transactional
    public Data data(Long est, Filter filter) {
        appointmentService.expire(est);
        LocalDate today = LocalDate.now(clock);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        LocalDateTime end7 = today.plusDays(7).atTime(LocalTime.MAX);
        LocalDate filterStart = filter.startDate() == null ? today : filter.startDate();
        LocalDate filterEnd = filter.endDate() == null ? filterStart : filter.endDate();
        if (filterEnd.isBefore(filterStart)) {
            filterEnd = filterStart;
        }
        List<Metric> metrics = List.of(
                new Metric("Agendamentos hoje", appointments.countByEstablishmentIdAndStartAtBetween(est, start, end), "Tudo que entrou para hoje"),
                new Metric("Pendentes", appointments.countByEstablishmentIdAndStatusAndStartAtBetween(est, AppointmentStatus.PENDING_APPROVAL, LocalDateTime.now(clock).minusYears(3), end7), "Esperando aprovação"),
                new Metric("Concluídos hoje", appointments.countByEstablishmentIdAndStatusAndStartAtBetween(est, AppointmentStatus.COMPLETED, start, end), "Atendimentos finalizados"),
                new Metric("Faltas hoje", appointments.countByEstablishmentIdAndStatusAndStartAtBetween(est, AppointmentStatus.NO_SHOW, start, end), "Clientes que não vieram"),
                new Metric("Próximos 7 dias", appointments.countByEstablishmentIdAndStatusInAndStartAtBetween(est, List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.PENDING_APPROVAL), start, end7), "Horários ativos")
        );
        List<Appointment> agenda = appointments.findAgenda(
                est,
                filterStart.atStartOfDay(),
                filterEnd.plusDays(1).atStartOfDay(),
                filter.professionalId(),
                filter.status()
        );
        return new Data(
                metrics,
                appointments.findByEstablishmentIdAndStatusOrderByStartAtAsc(est, AppointmentStatus.PENDING_APPROVAL),
                week(est, today, filter.professionalId()),
                appointments.findByEstablishmentIdAndStartAtBetweenOrderByStartAtAsc(est, start, end),
                agenda,
                appointments.findTop20ByEstablishmentIdAndStatusInOrderByStartAtDesc(est, List.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW, AppointmentStatus.COMPLETED, AppointmentStatus.EXPIRED))
        );
    }

    @Transactional
    public Reports reports(Long est) {
        appointmentService.expire(est);
        YearMonth month = YearMonth.now(clock);
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        long completed = appointments.countByEstablishmentIdAndStatusAndStartAtBetween(est, AppointmentStatus.COMPLETED, start, end);
        long noShows = appointments.countByEstablishmentIdAndStatusAndStartAtBetween(est, AppointmentStatus.NO_SHOW, start, end);
        long cancelled = appointments.countByEstablishmentIdAndStatusAndStartAtBetween(est, AppointmentStatus.CANCELLED, start, end);
        long confirmed = appointments.countByEstablishmentIdAndStatusInAndStartAtBetween(est, List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.PENDING_APPROVAL), start, end);
        BigDecimal expectedRevenue = appointments.sumServicePricesByStatusInAndStartAtBetween(est, List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.PENDING_APPROVAL, AppointmentStatus.COMPLETED), start, end);
        if (expectedRevenue == null) {
            expectedRevenue = BigDecimal.ZERO;
        }
        long totalRelevant = completed + noShows + cancelled + confirmed;
        String noShowRate = totalRelevant == 0 ? "0%" : Math.round((noShows * 100.0) / totalRelevant) + "%";
        List<ReportMetric> metrics = List.of(
                new ReportMetric("Atendimentos concluídos", String.valueOf(completed), "Finalizados no mês atual"),
                new ReportMetric("Faltas", String.valueOf(noShows), "Clientes que não compareceram"),
                new ReportMetric("Cancelamentos", String.valueOf(cancelled), "Cancelados no mês atual"),
                new ReportMetric("Horários ativos", String.valueOf(confirmed), "Confirmados ou pendentes no mês"),
                new ReportMetric("Faturamento previsto", "R$ " + expectedRevenue, "Soma dos preços cadastrados"),
                new ReportMetric("Taxa de faltas", noShowRate, "Baseada nos registros do mês")
        );
        return new Reports(metrics, appointments.findTop20ByEstablishmentIdAndStatusInOrderByStartAtDesc(est, List.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW, AppointmentStatus.COMPLETED, AppointmentStatus.EXPIRED)));
    }

    private List<Day> week(Long est, LocalDate today, Long professionalId) {
        List<Day> list = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = today.plusDays(i);
            List<Appointment> dayAppointments;
            if (professionalId == null) {
                dayAppointments = appointments.findByEstablishmentIdAndStatusInAndStartAtBetweenOrderByStartAtAsc(est, List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.PENDING_APPROVAL), day.atStartOfDay(), day.plusDays(1).atStartOfDay());
            } else {
                dayAppointments = appointments.findAgenda(est, day.atStartOfDay(), day.plusDays(1).atStartOfDay(), professionalId, null).stream()
                        .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED || a.getStatus() == AppointmentStatus.PENDING_APPROVAL)
                        .toList();
            }
            list.add(new Day(day, label(day.getDayOfWeek()), dayAppointments));
        }
        return list;
    }

    private String label(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "Seg";
            case TUESDAY -> "Ter";
            case WEDNESDAY -> "Qua";
            case THURSDAY -> "Qui";
            case FRIDAY -> "Sex";
            case SATURDAY -> "Sab";
            case SUNDAY -> "Dom";
        };
    }
}
