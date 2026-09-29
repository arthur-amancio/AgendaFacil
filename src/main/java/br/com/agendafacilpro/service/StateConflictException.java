package br.com.agendafacilpro.service;

public class StateConflictException extends BusinessException {

    public StateConflictException(String publicMessage) {
        super(publicMessage);
    }

    protected StateConflictException(String publicMessage, Throwable cause) {
        super(publicMessage, cause);
    }
}
