package br.com.agendafacilpro.service;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String publicMessage) {
        super(publicMessage);
    }
}
