package br.com.agendafacilpro.service;

public class BookingConflictException extends IllegalStateException {

    public static final String MESSAGE = "Esse horário acabou de ser reservado. Escolha outro horário.";

    public BookingConflictException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
