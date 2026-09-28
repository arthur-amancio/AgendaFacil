package br.com.agendafacilpro.config;

import java.time.DateTimeException;
import java.time.ZoneId;

import org.springframework.context.ApplicationContextException;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

final class RuntimeEnvironmentValidator {
    private static final String PROD = "prod";
    private static final String DEV = "dev";
    private static final String DEMO = "demo";
    private static final String PROVISIONING = "provisioning";

    private RuntimeEnvironmentValidator() {
    }

    static void validate(Environment environment) {
        boolean prod = isActive(environment, PROD);

        if (prod && isActive(environment, DEV)) {
            throw failure("Os profiles prod e dev não podem ser ativados juntos.");
        }
        if (prod && isActive(environment, DEMO)) {
            throw failure("Os profiles prod e demo não podem ser ativados juntos.");
        }
        if (isActive(environment, PROVISIONING) && !prod) {
            throw failure("O profile provisioning exige o profile prod.");
        }
        if (!prod) {
            return;
        }

        requireNonBlank(environment, "DB_URL");
        requireNonBlank(environment, "DB_USERNAME");
        requireNonBlank(environment, "DB_PASSWORD");
        String timeZone = requireNonBlank(environment, "APP_TIME_ZONE");
        validateTimeZone(timeZone);
    }

    private static boolean isActive(Environment environment, String profile) {
        return environment.acceptsProfiles(Profiles.of(profile));
    }

    private static String requireNonBlank(Environment environment, String property) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            throw failure(property + " é obrigatório e não pode estar vazio em produção.");
        }
        return value.trim();
    }

    private static void validateTimeZone(String timeZone) {
        try {
            ZoneId.of(timeZone);
        } catch (DateTimeException exception) {
            throw failure("APP_TIME_ZONE deve identificar um timezone válido.");
        }
    }

    private static ApplicationContextException failure(String message) {
        return new ApplicationContextException("Configuração de startup recusada: " + message);
    }
}
