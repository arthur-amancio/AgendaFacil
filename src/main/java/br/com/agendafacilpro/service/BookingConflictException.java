package br.com.agendafacilpro.service;

public class BookingConflictException extends StateConflictException {

    public static final String MESSAGE = "Esse horário acabou de ser reservado. Escolha outro horário.";

    public BookingConflictException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
