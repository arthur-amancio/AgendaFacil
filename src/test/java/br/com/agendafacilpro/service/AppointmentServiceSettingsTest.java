package br.com.agendafacilpro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.agendafacilpro.domain.Appointment;
import br.com.agendafacilpro.domain.AppointmentStatus;
import br.com.agendafacilpro.domain.Customer;
import br.com.agendafacilpro.domain.Establishment;
import br.com.agendafacilpro.domain.EstablishmentBusinessHours;
import br.com.agendafacilpro.domain.EstablishmentSettings;
import br.com.agendafacilpro.domain.Professional;
import br.com.agendafacilpro.domain.ServiceItem;
import br.com.agendafacilpro.repo.AppointmentRepo;
import br.com.agendafacilpro.repo.EstablishmentBusinessHoursRepo;
import br.com.agendafacilpro.repo.CustomerRepo;
import br.com.agendafacilpro.repo.ProfessionalRepo;
import br.com.agendafacilpro.repo.ServiceItemRepo;
import br.com.agendafacilpro.repo.TimeBlockRepo;
import br.com.agendafacilpro.ui.AppointmentViewUtil;

class AppointmentServiceSettingsTest {

    private final AppointmentRepo appointments = mock(AppointmentRepo.class);
    private final CustomerRepo customers = mock(CustomerRepo.class);
    private final ServiceItemRepo services = mock(ServiceItemRepo.class);
    private final ProfessionalRepo professionals = mock(ProfessionalRepo.class);
    private final TimeBlockRepo blocks = mock(TimeBlockRepo.class);
    private final FakeSettingsService settingsService = new FakeSettingsService();
    private final BookingGuardService guard = new BookingGuardService(null, settingsService) {
        @Override
        public Decision check(Establishment est, String phone, String ip, String honeypot) {
            return new Decision(true, "ok", "17988887777");
        }
    };
    private final EstablishmentBusinessHoursRepo hoursRepo = mock(EstablishmentBusinessHoursRepo.class);
    private final BusinessHoursService businessHours = new BusinessHoursService(hoursRepo);
    private final AppointmentService service = new AppointmentService(
            appointments,
            customers,
            services,
            professionals,
            blocks,
            guard,
            new AppointmentViewUtil(),
            new AppointmentAuditService(null),
            settingsService,
            businessHours,
            Clock.system(ZoneId.of("America/Sao_Paulo"))
    );

    private Establishment establishment;
    private ServiceItem serviceItem;
    private Professional professional;

    @BeforeEach
    void setUp() {
        establishment = establishment();
        serviceItem = serviceItem(establishment, 60);
        professional = professional(establishment);
        professional.getServices().add(serviceItem);
        settingsService.settings = EstablishmentSettings.defaultsFor(establishment);
        EstablishmentBusinessHours hours = new EstablishmentBusinessHours();
        hours.setEstablishment(establishment);
        hours.setDayOfWeek(DayOfWeek.MONDAY);
        hours.setOpen(true);
        hours.setOpeningTime(LocalTime.of(8, 0));
        hours.setClosingTime(LocalTime.of(18, 0));
        when(hoursRepo.findByEstablishmentIdAndDayOfWeek(eq(1L), any(DayOfWeek.class))).thenReturn(Optional.of(hours));

        when(appointments.findByEstablishmentIdAndStatusAndStartAtBefore(eq(1L), eq(AppointmentStatus.PENDING_APPROVAL), any(LocalDateTime.class))).thenReturn(List.of());
        when(appointments.findByEstablishmentIdAndStatusAndCreatedAtBefore(eq(1L), eq(AppointmentStatus.PENDING_APPROVAL), any(LocalDateTime.class))).thenReturn(List.of());
        when(services.findByIdAndEstablishmentId(2L, 1L)).thenReturn(Optional.of(serviceItem));
        when(professionals.findByIdAndEstablishmentId(3L, 1L)).thenReturn(Optional.of(professional));
        when(blocks.existsOverlap(eq(1L), eq(3L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(false);
        when(appointments.existsBlockingOverlap(eq(1L), eq(3L), any(LocalDateTime.class), any(LocalDateTime.class), any(), any())).thenReturn(false);
        when(appointments.countFutureByPhone(eq(1L), eq("17988887777"), any(), any(LocalDateTime.class))).thenReturn(0L);
        when(customers.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void newCustomerRequiresApprovalWhenConfigured() {
        when(customers.findByEstablishmentIdAndPhoneNormalized(1L, "17988887777")).thenReturn(Optional.empty());

        Appointment appointment = create();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING_APPROVAL);
        assertThat(appointment.getPublicToken()).isNotBlank();
    }

    @Test
    void newCustomerCanBeConfirmedAutomaticallyWhenConfigured() {
        settingsService.settings.setNewClientRequiresApproval(false);
        when(customers.findByEstablishmentIdAndPhoneNormalized(1L, "17988887777")).thenReturn(Optional.empty());

        Appointment appointment = create();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void futureAppointmentLimitByPhoneBlocksPublicBooking() {
        when(appointments.countFutureByPhone(eq(1L), eq("17988887777"), any(), any(LocalDateTime.class))).thenReturn(3L);

        assertThatThrownBy(this::create)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fale com o estabelecimento");
    }

    @Test
    void customerWithNoShowsRequiresApprovalByConfiguredLimit() {
        settingsService.settings.setNewClientRequiresApproval(false);
        settingsService.settings.setNoShowCountForManualApproval(2);
        Customer customer = customer(establishment, 2, false);
        when(customers.findByEstablishmentIdAndPhoneNormalized(1L, "17988887777")).thenReturn(Optional.of(customer));

        Appointment appointment = create();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING_APPROVAL);
    }

    @Test
    void customerBlockedByFlagDoesNotBookOnline() {
        Customer customer = customer(establishment, 0, true);
        when(customers.findByEstablishmentIdAndPhoneNormalized(1L, "17988887777")).thenReturn(Optional.of(customer));

        assertThatThrownBy(this::create)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fale com o estabelecimento");
    }

    @Test
    void customerBlockedByNoShowLimitDoesNotBookOnline() {
        settingsService.settings.setNoShowCountForBlock(3);
        Customer customer = customer(establishment, 3, false);
        when(customers.findByEstablishmentIdAndPhoneNormalized(1L, "17988887777")).thenReturn(Optional.of(customer));

        assertThatThrownBy(this::create)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fale com o estabelecimento");
    }

    @Test
    void longServiceRequiresApproval() {
        settingsService.settings.setNewClientRequiresApproval(false);
        settingsService.settings.setLongServiceManualApprovalMinutes(45);
        when(customers.findByEstablishmentIdAndPhoneNormalized(1L, "17988887777")).thenReturn(Optional.empty());

        Appointment appointment = create();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING_APPROVAL);
    }

    @Test
    void expiredPendingReleasesScheduleByChangingStatus() {
        Appointment pending = new Appointment();
        pending.setStatus(AppointmentStatus.PENDING_APPROVAL);
        when(appointments.findByEstablishmentIdAndStatusAndStartAtBefore(eq(1L), eq(AppointmentStatus.PENDING_APPROVAL), any(LocalDateTime.class))).thenReturn(List.of());
        when(appointments.findByEstablishmentIdAndStatusAndCreatedAtBefore(eq(1L), eq(AppointmentStatus.PENDING_APPROVAL), any(LocalDateTime.class))).thenReturn(List.of(pending));

        service.expire(1L, settingsService.settings);

        assertThat(pending.getStatus()).isEqualTo(AppointmentStatus.EXPIRED);
        verify(appointments).flush();
    }

    @Test
    void approveDoesNotConfirmExpiredPending() {
        Appointment pending = new Appointment();
        pending.setStatus(AppointmentStatus.PENDING_APPROVAL);
        LocalDateTime saoPauloNow = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        pending.setStartAt(saoPauloNow.plusDays(1));
        pending.setCreatedAt(saoPauloNow.minusHours(2));
        when(appointments.findByIdAndEstablishmentId(99L, 1L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.approve(99L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expirou antes da aprovação");

        assertThat(pending.getStatus()).isEqualTo(AppointmentStatus.EXPIRED);
        assertThat(pending.getCancellationReason()).isEqualTo("Reserva pendente expirou antes da aprovação");
    }

    private Appointment create() {
        return service.create(establishment, 2L, 3L, LocalDate.now().plusDays(1), LocalTime.of(9, 0), "Ana Cliente", "(17) 98888-7777", "127.0.0.1", "");
    }

    private Establishment establishment() {
        Establishment e = new Establishment();
        e.setId(1L);
        e.setName("Estabelecimento Teste");
        e.setSlug("teste");
        e.setWhatsapp("5517999999999");
        return e;
    }

    private ServiceItem serviceItem(Establishment establishment, int duration) {
        ServiceItem s = new ServiceItem();
        s.setId(2L);
        s.setEstablishment(establishment);
        s.setName("Corte");
        s.setDurationMinutes(duration);
        s.setActive(true);
        return s;
    }

    private Professional professional(Establishment establishment) {
        Professional p = new Professional();
        p.setId(3L);
        p.setEstablishment(establishment);
        p.setName("Mariana");
        p.setActive(true);
        return p;
    }

    private Customer customer(Establishment establishment, int noShowCount, boolean blocked) {
        Customer c = new Customer();
        c.setEstablishment(establishment);
        c.setName("Ana Cliente");
        c.setPhoneNormalized("17988887777");
        c.setNoShowCount(noShowCount);
        c.setBlocked(blocked);
        return c;
    }

    private static class FakeSettingsService extends EstablishmentSettingsService {
        private EstablishmentSettings settings;

        FakeSettingsService() {
            super(null);
        }

        @Override
        public EstablishmentSettings forEstablishment(Establishment establishment) {
            return settings;
        }

        @Override
        public EstablishmentSettings forEstablishmentId(Long establishmentId) {
            return settings;
        }
    }
}
