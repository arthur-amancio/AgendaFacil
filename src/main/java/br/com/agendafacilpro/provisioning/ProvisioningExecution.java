package br.com.agendafacilpro.provisioning;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("provisioning")
public class ProvisioningExecution {
    private int exitCode = 2;
    private String message = "O comando de provisioning não foi executado.";

    public void success(String message) {
        this.exitCode = 0;
        this.message = message;
    }

    public void failure(String message) {
        this.exitCode = 2;
        this.message = message;
    }

    public int exitCode() { return exitCode; }
    public String message() { return message; }
}
