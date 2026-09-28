package br.com.agendafacilpro.provisioning;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class InitialCredentialGenerator {
    static final int ENTROPY_BYTES = 24;

    private final SecureRandom secureRandom;

    public InitialCredentialGenerator() {
        this(new SecureRandom());
    }

    InitialCredentialGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public char[] generate() {
        byte[] entropy = new byte[ENTROPY_BYTES];
        secureRandom.nextBytes(entropy);
        byte[] encoded = Base64.getUrlEncoder().withoutPadding().encode(entropy);
        char[] credential = new char[encoded.length];
        for (int index = 0; index < encoded.length; index++) {
            credential[index] = (char) encoded[index];
            encoded[index] = 0;
        }
        java.util.Arrays.fill(entropy, (byte) 0);
        return credential;
    }
}
