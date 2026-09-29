package br.com.agendafacilpro.service;

/**
 * Base para erros de negócio cuja mensagem foi escrita para exibição ao usuário.
 * Exceções que não pertencem a esta hierarquia nunca devem ter sua mensagem
 * original enviada para a camada HTTP.
 */
public abstract class BusinessException extends RuntimeException {

    private final String publicMessage;

    protected BusinessException(String publicMessage) {
        super(publicMessage);
        this.publicMessage = publicMessage;
    }

    protected BusinessException(String publicMessage, Throwable cause) {
        super(publicMessage, cause);
        this.publicMessage = publicMessage;
    }

    public final String publicMessage() {
        return publicMessage;
    }
}
