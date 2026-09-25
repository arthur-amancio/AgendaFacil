package br.com.agendafacilpro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.agendafacilpro.domain.Appointment;
import br.com.agendafacilpro.domain.AppointmentStatus;
import br.com.agendafacilpro.domain.Customer;
import br.com.agendafacilpro.domain.Establishment;
import br.com.agendafacilpro.domain.EstablishmentSettings;
import br.com.agendafacilpro.domain.EstablishmentBusinessHours;
import br.com.agendafacilpro.domain.Professional;
import br.com.agendafacilpro.domain.ServiceItem;
import br.com.agendafacilpro.repo.AppointmentRepo;
import br.com.agendafacilpro.repo.CustomerRepo;
import br.com.agendafacilpro.repo.ProfessionalRepo;
import br.com.agendafacilpro.repo.ServiceItemRepo;
import br.com.agendafacilpro.repo.TimeBlockRepo;
import br.com.agendafacilpro.ui.AppointmentViewUtil;

class AppointmentServiceNoShowTest {

    private final AppointmentRepo appointments = mock(AppointmentRepo.class);
    private final CustomerRepo customers = mock(CustomerRepo.class);
    private final ServiceItemRepo services = mock(ServiceItemRepo.class);
    private final ProfessionalRepo professionals = mock(ProfessionalRepo.class);
    private final TimeBlockRepo blocks = mock(TimeBlockRepo.class);
    private final FakeSettingsService settings = new FakeSettingsService();
    private final BookingGuardService guard = new BookingGuardService(null, settings) {
        @Override
        public Decision check(Establishment est, String phone, String ip, String honeypot) {
            return new Decision(true, "ok", "17988887777");
        }
    };
    private final BusinessHoursService businessHours = mock(BusinessHoursService.class);
    private final AppointmentService service = new AppointmentService(
            appointments,
            customers,
            services,
            professionals,
            blocks,
            guard,
            new AppointmentViewUtil(),
            new AppointmentAuditService(null),
            settings,
            businessHours,
            Clock.system(ZoneId.of("America/Sao_Paulo"))
    );

    @Test
    void noShowIncrementsCustomerCounter() {
        Customer customer = new Customer();
        customer.setNoShowCount(1);
        Appointment appointment = new Appointment();
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setCustomer(customer);

        when(appointments.findByIdAndEstablishmentId(10L, 1L)).thenReturn(Optional.of(appointment));

        service.noShow(10L, 1L);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
        assertThat(customer.getNoShowCount()).isEqualTo(2);
    }

    @Test
    void customerWithTwoNoShowsRequiresManualApprovalOnNextBooking() {
        Establishment establishment = establishment();
        ServiceItem serviceItem = serviceItem(establishment);
        Professional professional = professional(establishment);
        professional.getServices().add(serviceItem);
        Customer customer = customer(establishment, 2, false);
        EstablishmentBusinessHours dailyHours = mock(EstablishmentBusinessHours.class);
        when(dailyHours.isOpen()).thenReturn(true);
        when(businessHours.hours(eq(1L), any(LocalDate.class))).thenReturn(Optional.of(dailyHours));
        when(businessHours.fits(eq(dailyHours), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(true);

        when(appointments.findByEstablishmentIdAndStatusAndStartAtBefore(eq(1L), eq(AppointmentStatus.PENDING_APPROVAL), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(appointments.findByEstablishmentIdAndStatusAndCreatedAtBefore(eq(1L), eq(AppointmentStatus.PENDING_APPROVAL), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(services.findByIdAndEstablishmentId(2L, 1L)).thenReturn(Optional.of(serviceItem));
        when(professionals.findByIdAndEstablishmentId(3L, 1L)).thenReturn(Optional.of(professional));
        when(blocks.existsOverlap(eq(1L), eq(3L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(false);
        when(appointments.existsBlockingOverlap(eq(1L), eq(3L), any(LocalDateTime.class), any(LocalDateTime.class), any(), any()))
                .thenReturn(false);
        when(customers.findByEstablishmentIdAndPhoneNormalized(1L, "17988887777")).thenReturn(Optional.of(customer));
        when(customers.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment appointment = service.create(
                establishment,
                2L,
                3L,
                LocalDate.now().plusDays(1),
                LocalTime.of(9, 0),
                "Ana Cliente",
                "(17) 98888-7777",
                "127.0.0.1",
                ""
        );

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING_APPROVAL);
    }

    private Establishment establishment() {
        Establishment establishment = new Establishment();
        establishment.setId(1L);
        establishment.setName("Estabelecimento Teste");
        establishment.setSlug("teste");
        establishment.setWhatsapp("5517999999999");
        return establishment;
    }

    private ServiceItem serviceItem(Establishment establishment) {
        ServiceItem serviceItem = new ServiceItem();
        serviceItem.setId(2L);
        serviceItem.setEstablishment(establishment);
        serviceItem.setName("Corte");
        serviceItem.setDurationMinutes(60);
        serviceItem.setActive(true);
        return serviceItem;
    }

    private Professional professional(Establishment establishment) {
        Professional professional = new Professional();
        professional.setId(3L);
        professional.setEstablishment(establishment);
        professional.setName("Mariana");
        professional.setActive(true);
        return professional;
    }

    private Customer customer(Establishment establishment, int noShowCount, boolean blocked) {
        Customer customer = new Customer();
        customer.setEstablishment(establishment);
        customer.setName("Ana Cliente");
        customer.setPhoneNormalized("17988887777");
        customer.setNoShowCount(noShowCount);
        customer.setBlocked(blocked);
        return customer;
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
