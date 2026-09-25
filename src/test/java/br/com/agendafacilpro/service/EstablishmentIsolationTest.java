package br.com.agendafacilpro.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.agendafacilpro.domain.Appointment;
import br.com.agendafacilpro.repo.AppointmentRepo;

class EstablishmentIsolationTest {

    private final AppointmentRepo appointments = mock(AppointmentRepo.class);
    private final AppointmentService service = new AppointmentService(appointments, null, null, null, null, null, null, null, null, null, null);

    @Test
    void establishmentBCannotApproveAppointmentFromA() {
        when(appointments.findByIdAndEstablishmentId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(10L, 2L, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(appointments, never()).save(org.mockito.ArgumentMatchers.any(Appointment.class));
    }

    @Test
    void establishmentBCannotCancelAppointmentFromA() {
        when(appointments.findByIdAndEstablishmentId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(10L, 2L, "teste", null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(appointments, never()).save(org.mockito.ArgumentMatchers.any(Appointment.class));
    }
}
