package br.com.agendafacilpro.web;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

import br.com.agendafacilpro.domain.Establishment;
import br.com.agendafacilpro.domain.EstablishmentSettings;
import br.com.agendafacilpro.domain.Professional;
import br.com.agendafacilpro.domain.ServiceItem;
import br.com.agendafacilpro.service.AppointmentService;
import br.com.agendafacilpro.service.BookingConflictException;
import br.com.agendafacilpro.service.CatalogService;
import br.com.agendafacilpro.service.EstablishmentSettingsService;
import br.com.agendafacilpro.service.ResourceNotFoundException;

class WebErrorHandlingTest {

    private static final String INTERNAL_DETAIL =
            "jdbc:postgresql://secret-host/internal password=super-secret";

    private CatalogService catalog;
    private AppointmentService appointments;
    private EstablishmentSettingsService settings;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        catalog = mock(CatalogService.class);
        appointments = mock(AppointmentService.class);
        settings = mock(EstablishmentSettingsService.class);
        PublicBookingController publicBooking = new PublicBookingController(
                catalog, appointments, settings, Clock.system(ZoneId.of("America/Sao_Paulo")));

        mvc = MockMvcBuilders.standaloneSetup(new AuthController(), publicBooking, new FailureProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rootRedirectsToLoginAndNeverToDemoTenant() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(content().string(not(containsString("agenda-demo"))));
    }

    @Test
    void unavailableTenantReturnsFriendlyNotFound() throws Exception {
        when(catalog.establishment("slug-inexistente"))
                .thenThrow(new ResourceNotFoundException("Esse link de agendamento não está disponível."));

        mvc.perform(get("/agenda/slug-inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("title", "Página não encontrada"))
                .andExpect(model().attribute("message", "Esse link de agendamento não está disponível."))
                .andExpect(content().string(not(containsString("ResourceNotFoundException"))));
    }

    @Test
    void concurrentBookingConflictReturns409WithoutDatabaseCause() throws Exception {
        Establishment establishment = establishment();
        ServiceItem service = service(establishment);
        Professional professional = professional(establishment, service);
        when(catalog.establishment("piloto")).thenReturn(establishment);
        when(catalog.service(2L, 1L)).thenReturn(service);
        when(catalog.professionalForService(3L, 2L, 1L)).thenReturn(professional);
        when(settings.forEstablishment(establishment)).thenReturn(EstablishmentSettings.defaultsFor(establishment));
        when(appointments.create(any(Establishment.class), eq(2L), eq(3L), any(LocalDate.class),
                any(LocalTime.class), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new BookingConflictException(new SQLException(INTERNAL_DETAIL, "23P01")));

        mvc.perform(validBooking("/agenda/piloto/confirmar"))
                .andExpect(status().isConflict())
                .andExpect(view().name("public/data"))
                .andExpect(model().attribute("error", BookingConflictException.MESSAGE))
                .andExpect(model().attribute("error", not(containsString("secret-host"))))
                .andExpect(model().attribute("error", not(containsString("super-secret"))));
    }

    @Test
    void invalidPublicFormReturns400WithControlledValidationMessage() throws Exception {
        when(catalog.establishment("piloto")).thenReturn(establishment());

        mvc.perform(post("/agenda/piloto/revisar"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error"))
                .andExpect(model().attributeExists("message"))
                .andExpect(model().attribute("message", not(containsString("typeMismatch"))))
                .andExpect(model().attribute("message", not(containsString("Exception"))));
    }

    @Test
    void unexpectedExceptionReturns500WithoutOriginalMessage() throws Exception {
        mvc.perform(get("/__failure/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("message", not(containsString("secret-host"))))
                .andExpect(model().attribute("message", not(containsString("super-secret"))))
                .andExpect(model().attribute("message", not(containsString("jdbc:"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("stacktrace"))));
    }

    @Test
    void arbitraryStandardExceptionsAreNotTreatedAsPublicMessages() throws Exception {
        for (String path : new String[]{"argument", "state"}) {
            mvc.perform(get("/__failure/" + path))
                    .andExpect(status().isInternalServerError())
                    .andExpect(view().name("error"))
                    .andExpect(model().attribute("message", not(containsString(INTERNAL_DETAIL))));
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validBooking(String path) {
        return post(path)
                .param("serviceId", "2")
                .param("professionalId", "3")
                .param("date", "2030-01-07")
                .param("time", "09:00")
                .param("customerName", "Ana Cliente")
                .param("customerPhone", "(17) 98888-7777")
                .param("website", "");
    }

    private Establishment establishment() {
        Establishment establishment = new Establishment();
        establishment.setId(1L);
        establishment.setName("Piloto");
        establishment.setSlug("piloto");
        return establishment;
    }

    private ServiceItem service(Establishment establishment) {
        ServiceItem service = new ServiceItem();
        service.setId(2L);
        service.setEstablishment(establishment);
        service.setName("Corte");
        service.setDurationMinutes(60);
        service.setActive(true);
        return service;
    }

    private Professional professional(Establishment establishment, ServiceItem service) {
        Professional professional = new Professional();
        professional.setId(3L);
        professional.setEstablishment(establishment);
        professional.setName("Ana");
        professional.setActive(true);
        professional.getServices().add(service);
        return professional;
    }

    @Controller
    static class FailureProbeController {

        @GetMapping("/__failure/unexpected")
        String unexpected() {
            throw new RuntimeException(INTERNAL_DETAIL);
        }

        @GetMapping("/__failure/argument")
        String argument() {
            throw new IllegalArgumentException(INTERNAL_DETAIL);
        }

        @GetMapping("/__failure/state")
        String state() {
            throw new IllegalStateException(INTERNAL_DETAIL);
        }
    }
}
