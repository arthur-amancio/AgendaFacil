package br.com.agendafacilpro.service;

public class InvalidRequestException extends BusinessException {

    public InvalidRequestException(String publicMessage) {
        super(publicMessage);
    }

    public InvalidRequestException(String publicMessage, Throwable cause) {
        super(publicMessage, cause);
    }
}
