package br.com.agendafacilpro.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Clock;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import br.com.agendafacilpro.domain.AppUser;
import br.com.agendafacilpro.domain.Establishment;
import br.com.agendafacilpro.domain.Professional;
import br.com.agendafacilpro.domain.ServiceItem;
import br.com.agendafacilpro.repo.CustomerRepo;
import br.com.agendafacilpro.repo.ProfessionalRepo;
import br.com.agendafacilpro.repo.ServiceItemRepo;
import br.com.agendafacilpro.repo.TimeBlockRepo;
import br.com.agendafacilpro.service.AppointmentService;
import br.com.agendafacilpro.service.BookingConflictException;
import br.com.agendafacilpro.service.BusinessHoursService;
import br.com.agendafacilpro.service.CurrentUserService;
import br.com.agendafacilpro.service.DashboardService;
import br.com.agendafacilpro.service.EstablishmentSettingsService;
import br.com.agendafacilpro.service.ManualAppointmentRequest;
import br.com.agendafacilpro.web.form.WeeklyBusinessHoursForm;

class PanelControllerCatalogPolicyTest {

    private final FakeCurrentUserService current = new FakeCurrentUserService();
    private final Clock clock = Clock.system(ZoneId.of("America/Sao_Paulo"));
    private final DashboardService dashboard = new DashboardService(null, null, clock);
    private final FakeAppointmentService appointments = new FakeAppointmentService();
    private final ServiceItemRepo services = mock(ServiceItemRepo.class);
    private final ProfessionalRepo professionals = mock(ProfessionalRepo.class);
    private final TimeBlockRepo blocks = mock(TimeBlockRepo.class);
    private final CustomerRepo customers = mock(CustomerRepo.class);
    private final EstablishmentSettingsService settings = new EstablishmentSettingsService(null);
    private final BusinessHoursService businessHours = mock(BusinessHoursService.class);
    private final PanelController controller = new PanelController(current, dashboard, appointments, services, professionals, blocks, customers, settings, businessHours, clock);

    private Establishment establishment;

    @BeforeEach
    void setUp() {
        establishment = new Establishment();
        establishment.setId(1L);
        establishment.setName("AgendaFácil Demo");
        AppUser user = new AppUser();
        user.setEstablishment(establishment);
        current.user = user;
    }

    @Test
    void serviceWithoutHistoryCanBeDeleted() {
        ServiceItem service = service();
        when(services.findByIdAndEstablishmentId(2L, 1L)).thenReturn(Optional.of(service));
        appointments.serviceCount = 0L;
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String route = controller.deleteService(2L, redirect);

        assertThat(route).isEqualTo("redirect:/panel/services");
        verify(services).delete(service);
    }

    @Test
    void weeklyHoursUpdateUsesAuthenticatedEstablishment() {
        WeeklyBusinessHoursForm form = new WeeklyBusinessHoursForm();
        form.setHours(Arrays.stream(DayOfWeek.values()).map(day -> {
            WeeklyBusinessHoursForm.DayHours hours = new WeeklyBusinessHoursForm.DayHours();
            hours.setDayOfWeek(day);
            hours.setOpen(false);
            return hours;
        }).toList());

        String route = controller.businessHours(form, new RedirectAttributesModelMap());

        assertThat(route).isEqualTo("redirect:/panel/settings");
        verify(businessHours).update(org.mockito.ArgumentMatchers.same(establishment), any());
    }

    @Test
    void serviceWithHistoryIsArchivedInsteadOfDeleted() {
        ServiceItem service = service();
        when(services.findByIdAndEstablishmentId(2L, 1L)).thenReturn(Optional.of(service));
        appointments.serviceCount = 1L;
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.deleteService(2L, redirect);

        assertThat(service.isActive()).isFalse();
        verify(services).save(service);
        assertThat(redirect.getFlashAttributes()).containsKey("error");
    }

    @Test
    void establishmentCannotDeleteServiceFromAnotherEstablishment() {
        when(services.findByIdAndEstablishmentId(99L, 1L)).thenReturn(Optional.empty());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String route = controller.deleteService(99L, redirect);

        assertThat(route).isEqualTo("redirect:/panel/services");
        verify(services, never()).delete(any(ServiceItem.class));
        assertThat(redirect.getFlashAttributes()).containsKey("error");
    }

    @Test
    void professionalWithoutHistoryCanBeDeleted() {
        Professional professional = professional();
        when(professionals.findByIdAndEstablishmentId(3L, 1L)).thenReturn(Optional.of(professional));
        appointments.professionalCount = 0L;
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String route = controller.deleteProfessional(3L, redirect);

        assertThat(route).isEqualTo("redirect:/panel/professionals");
        verify(professionals).delete(professional);
    }

    @Test
    void professionalWithHistoryIsArchivedInsteadOfDeleted() {
        Professional professional = professional();
        when(professionals.findByIdAndEstablishmentId(3L, 1L)).thenReturn(Optional.of(professional));
        appointments.professionalCount = 1L;
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.deleteProfessional(3L, redirect);

        assertThat(professional.isActive()).isFalse();
        verify(professionals).save(professional);
        assertThat(redirect.getFlashAttributes()).containsKey("error");
    }

    @Test
    void establishmentCannotDeleteProfessionalFromAnotherEstablishment() {
        when(professionals.findByIdAndEstablishmentId(99L, 1L)).thenReturn(Optional.empty());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String route = controller.deleteProfessional(99L, redirect);

        assertThat(route).isEqualTo("redirect:/panel/professionals");
        verify(professionals, never()).delete(any(Professional.class));
        assertThat(redirect.getFlashAttributes()).containsKey("error");
    }

    @Test
    void concurrentDatabaseConflictReturnsHumanMessageToPanel() {
        appointments.conflictOnCreate = true;
        ManualAppointmentRequest request = new ManualAppointmentRequest(
                "Ana Cliente",
                "(17) 98888-7777",
                2L,
                3L,
                LocalDate.now().plusDays(1),
                LocalTime.of(9, 0),
                null,
                false
        );
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String route = controller.manual(
                request,
                new BeanPropertyBindingResult(request, "manualAppointmentRequest"),
                redirect
        );

        assertThat(route).isEqualTo("redirect:/panel/appointments");
        assertThat(redirect.getFlashAttributes().get("error")).isEqualTo(BookingConflictException.MESSAGE);
    }

    private ServiceItem service() {
        ServiceItem service = new ServiceItem();
        service.setId(2L);
        service.setEstablishment(establishment);
        service.setName("Atendimento Completo");
        service.setDurationMinutes(60);
        service.setActive(true);
        return service;
    }

    private Professional professional() {
        Professional professional = new Professional();
        professional.setId(3L);
        professional.setEstablishment(establishment);
        professional.setName("Profissional 1");
        professional.setActive(true);
        return professional;
    }

    private static class FakeCurrentUserService extends CurrentUserService {
        private AppUser user;

        FakeCurrentUserService() {
            super(null);
        }

        @Override
        public AppUser user() {
            return user;
        }

        @Override
        public Long establishmentId() {
            return user.getEstablishment().getId();
        }
    }

    private static class FakeAppointmentService extends AppointmentService {
        private long serviceCount;
        private long professionalCount;
        private boolean conflictOnCreate;

        FakeAppointmentService() {
            super(null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public long countAppointmentsForService(Long establishmentId, Long serviceId) {
            return serviceCount;
        }

        @Override
        public long countAppointmentsForProfessional(Long establishmentId, Long professionalId) {
            return professionalCount;
        }

        @Override
        public br.com.agendafacilpro.domain.Appointment createManual(
                Establishment establishment,
                AppUser user,
                ManualAppointmentRequest request
        ) {
            if (conflictOnCreate) {
                throw new BookingConflictException(new IllegalStateException("simulated database conflict"));
            }
            return new br.com.agendafacilpro.domain.Appointment();
        }
    }
}
