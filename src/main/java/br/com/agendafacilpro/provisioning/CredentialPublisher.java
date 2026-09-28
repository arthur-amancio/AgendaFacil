package br.com.agendafacilpro.provisioning;

public interface CredentialPublisher {
    CredentialChannel prepare(String outputFile);

    interface CredentialChannel {
        void publish(char[] credential);
    }
}
