package br.com.agendafacilpro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import br.com.agendafacilpro.domain.AppUser;
import br.com.agendafacilpro.domain.Appointment;
import br.com.agendafacilpro.domain.AppointmentStatus;
import br.com.agendafacilpro.domain.Customer;
import br.com.agendafacilpro.domain.Establishment;
import br.com.agendafacilpro.domain.EstablishmentSettings;
import br.com.agendafacilpro.domain.Professional;
import br.com.agendafacilpro.domain.ServiceItem;
import br.com.agendafacilpro.repo.AppointmentRepo;
import br.com.agendafacilpro.repo.CustomerRepo;
import br.com.agendafacilpro.repo.ProfessionalRepo;
import br.com.agendafacilpro.repo.ServiceItemRepo;
import br.com.agendafacilpro.repo.TimeBlockRepo;
import br.com.agendafacilpro.ui.AppointmentViewUtil;

class AppointmentServiceManualTest {

    private final AppointmentRepo appointments = mock(AppointmentRepo.class);
    private final CustomerRepo customers = mock(CustomerRepo.class);
    private final ServiceItemRepo services = mock(ServiceItemRepo.class);
    private final ProfessionalRepo professionals = mock(ProfessionalRepo.class);
    private final TimeBlockRepo blocks = mock(TimeBlockRepo.class);
    private final FakeSettingsService settings = new FakeSettingsService();
    private final BookingGuardService guard = new BookingGuardService(null, settings);
    private final FakeAuditService audit = new FakeAuditService();
    private final AppointmentService service = new AppointmentService(
            appointments,
            customers,
            services,
            professionals,
            blocks,
            guard,
            new AppointmentViewUtil(),
            audit,
            settings
    );

    private Establishment establishment;
    private AppUser user;
    private ServiceItem serviceItem;
    private Professional professional;

    @BeforeEach
    void setUp() {
        establishment = establishment();
        user = user(establishment);
        serviceItem = serviceItem(establishment);
        professional = professional(establishment);
        professional.getServices().add(serviceItem);

        when(appointments.findByEstablishmentIdAndStatusAndStartAtBefore(eq(1L), eq(AppointmentStatus.PENDING_APPROVAL), any(LocalDateTime.class))).thenReturn(List.of());
        when(appointments.findByEstablishmentIdAndStatusAndCreatedAtBefore(eq(1L), eq(AppointmentStatus.PENDING_APPROVAL), any(LocalDateTime.class))).thenReturn(List.of());
        when(services.findByIdAndEstablishmentId(2L, 1L)).thenReturn(Optional.of(serviceItem));
        when(professionals.findByIdAndEstablishmentId(3L, 1L)).thenReturn(Optional.of(professional));
        when(blocks.existsOverlap(eq(1L), eq(3L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(false);
        when(appointments.existsBlockingOverlap(eq(1L), eq(3L), any(LocalDateTime.class), any(LocalDateTime.class), any(), any())).thenReturn(false);
        when(customers.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void manualBookingCreatesCustomerAndStaysConfirmed() {
        when(customers.findByEstablishmentIdAndPhoneNormalized(1L, "17988887777")).thenReturn(Optional.empty());

        Appointment appointment = service.createManual(establishment, user, request("Ana Cliente", "(17) 98888-7777"));

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getPhoneNormalized()).isEqualTo("17988887777");
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointment.getPublicToken()).isNotBlank();
        assertThat(appointment.getInternalNote()).isEqualTo("chegou pelo telefone");
        assertThat(audit.lastAction).isEqualTo("MANUAL_CREATE");
    }

    @Test
    void manualBookingReusesCustomerByNormalizedPhone() {
        Customer existing = customer(establishment, 0, false);
        when(customers.findByEstablishmentIdAndPhoneNormalized(1L, "17988887777")).thenReturn(Optional.of(existing));

        Appointment appointment = service.createManual(establishment, user, request("Ana Atualizada", "+55 (17) 98888-7777"));

        assertThat(appointment.getCustomer()).isSameAs(existing);
        assertThat(existing.getName()).isEqualTo("Ana Atualizada");
        assertThat(audit.lastDetails).contains("Cliente reutilizado");
    }

    @Test
    void manualBookingInOccupiedTimeFails() {
        when(appointments.existsBlockingOverlap(eq(1L), eq(3L), any(LocalDateTime.class), any(LocalDateTime.class), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.createManual(establishment, user, request("Ana Cliente", "(17) 98888-7777")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não está disponível para este serviço");

        verify(appointments, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void databaseOverlapIsTranslatedToHumanMessage() {
        when(customers.findByEstablishmentIdAndPhoneNormalized(1L, "17988887777")).thenReturn(Optional.empty());
        when(appointments.saveAndFlush(any(Appointment.class))).thenThrow(new DataIntegrityViolationException(
                "constraint violation",
                new SQLException("conflicting key", "23P01")
        ));

        assertThatThrownBy(() -> service.createManual(establishment, user, request("Ana Cliente", "(17) 98888-7777")))
                .isInstanceOf(BookingConflictException.class)
                .hasMessage(BookingConflictException.MESSAGE)
                .hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    void manualBookingRejectsProfessionalNotLinkedToService() {
        professional.getServices().clear();

        assertThatThrownBy(() -> service.createManual(establishment, user, request("Ana Cliente", "(17) 98888-7777")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não realiza o serviço escolhido");
    }

    @Test
    void manualBookingRejectsManipulatedTimeOutsideSlotGrid() {
        assertThatThrownBy(() -> service.createManual(establishment, user, requestAt("Ana Cliente", "(17) 98888-7777", LocalTime.of(9, 10))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não está disponível para este serviço");
    }

    @Test
    void manualBookingRejectsTimeOutsideBusinessHours() {
        assertThatThrownBy(() -> service.createManual(establishment, user, requestAt("Ana Cliente", "(17) 98888-7777", LocalTime.of(18, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não está disponível para este serviço");
    }

    @Test
    void manualBookingExpiresOldPendingBeforeCheckingConflict() {
        when(customers.findByEstablishmentIdAndPhoneNormalized(1L, "17988887777")).thenReturn(Optional.empty());

        service.createManual(establishment, user, request("Ana Cliente", "(17) 98888-7777"));

        InOrder order = inOrder(appointments);
        order.verify(appointments).findByEstablishmentIdAndStatusAndStartAtBefore(eq(1L), eq(AppointmentStatus.PENDING_APPROVAL), any(LocalDateTime.class));
        order.verify(appointments).findByEstablishmentIdAndStatusAndCreatedAtBefore(eq(1L), eq(AppointmentStatus.PENDING_APPROVAL), any(LocalDateTime.class));
        order.verify(appointments).existsBlockingOverlap(eq(1L), eq(3L), any(LocalDateTime.class), any(LocalDateTime.class), any(), any());
    }

    @Test
    void summaryUsesPublicTokenInsteadOfSequentialId() {
        Appointment appointment = new Appointment();
        appointment.setEstablishment(establishment);
        appointment.setCustomer(customer(establishment, 0, false));
        appointment.setServiceItem(serviceItem);
        appointment.setProfessional(professional);
        appointment.setStartAt(LocalDateTime.now().plusDays(1));
        appointment.setEndAt(LocalDateTime.now().plusDays(1).plusHours(1));
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setPublicToken("public-token-123456789012");
        when(appointments.findByEstablishmentIdAndPublicToken(1L, "public-token-123456789012")).thenReturn(Optional.of(appointment));

        AppointmentService.Summary summary = service.summary(establishment, "public-token-123456789012");

        assertThat(summary.customer()).isEqualTo("Ana Cliente");
        verify(appointments, never()).findByIdAndEstablishmentId(any(), any());
    }

    @Test
    void manualBookingBlocksPublicSlot() {
        when(customers.findByEstablishmentIdAndPhoneNormalized(1L, "17988887777")).thenReturn(Optional.empty());
        service.createManual(establishment, user, request("Ana Cliente", "(17) 98888-7777"));
        when(appointments.existsBlockingOverlap(eq(1L), eq(3L), any(LocalDateTime.class), any(LocalDateTime.class), any(), any())).thenReturn(true);

        List<AppointmentService.Slot> slots = service.slots(1L, 2L, 3L, LocalDate.now().plusDays(1));

        assertThat(slots).anySatisfy(slot -> assertThat(slot.available()).isFalse());
    }

    @Test
    void cancelReleasesScheduleByChangingStatus() {
        Appointment appointment = new Appointment();
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setEstablishment(establishment);
        appointment.setCustomer(customer(establishment, 0, false));
        when(appointments.findByIdAndEstablishmentId(10L, 1L)).thenReturn(Optional.of(appointment));

        service.cancel(10L, 1L, "Cliente pediu remarcacao", user);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getCancellationReason()).isEqualTo("Cliente pediu remarcacao");
        assertThat(audit.lastAction).isEqualTo("CANCEL");
    }

    private ManualAppointmentRequest request(String name, String phone) {
        return new ManualAppointmentRequest(name, phone, 2L, 3L, LocalDate.now().plusDays(1), LocalTime.of(9, 0), "chegou pelo telefone", false);
    }

    private ManualAppointmentRequest requestAt(String name, String phone, LocalTime time) {
        return new ManualAppointmentRequest(name, phone, 2L, 3L, LocalDate.now().plusDays(1), time, "chegou pelo telefone", false);
    }

    private Establishment establishment() {
        Establishment e = new Establishment();
        e.setId(1L);
        e.setName("Estabelecimento Teste");
        e.setSlug("teste");
        e.setWhatsapp("5517999999999");
        return e;
    }

    private AppUser user(Establishment establishment) {
        AppUser user = new AppUser();
        user.setId(5L);
        user.setName("Dona Maria");
        user.setEmail("maria@example.com");
        user.setEstablishment(establishment);
        return user;
    }

    private ServiceItem serviceItem(Establishment establishment) {
        ServiceItem s = new ServiceItem();
        s.setId(2L);
        s.setEstablishment(establishment);
        s.setName("Corte");
        s.setDurationMinutes(60);
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

    private static class FakeAuditService extends AppointmentAuditService {
        private String lastAction;
        private String lastDetails;

        FakeAuditService() {
            super(null);
        }

        @Override
        public void record(Appointment appointment, AppUser user, String action, String details) {
            lastAction = action;
            lastDetails = details;
        }
    }

    private static class FakeSettingsService extends EstablishmentSettingsService {
        FakeSettingsService() {
            super(null);
        }

        @Override
        public EstablishmentSettings forEstablishment(Establishment establishment) {
            return EstablishmentSettings.defaultsFor(establishment);
        }

        @Override
        public EstablishmentSettings forEstablishmentId(Long establishmentId) {
            Establishment establishment = new Establishment();
            establishment.setId(establishmentId);
            return EstablishmentSettings.defaultsFor(establishment);
        }
    }
}
