package br.com.agendafacilpro;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;

import br.com.agendafacilpro.provisioning.ProvisioningExecution;

@SpringBootApplication
public class AgendaFacilProApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AgendaFacilProApplication.class);
        if (provisioningRequested(args)) {
            application.setWebApplicationType(WebApplicationType.NONE);
        }

        ConfigurableApplicationContext context = application.run(args);
        if (context.getEnvironment().acceptsProfiles(Profiles.of("provisioning"))) {
            ProvisioningExecution execution = context.getBean(ProvisioningExecution.class);
            if (execution.exitCode() == 0) {
                System.out.println(execution.message());
            } else {
                System.err.println(execution.message());
            }
            int exitCode = SpringApplication.exit(context, execution::exitCode);
            System.exit(exitCode);
        }
    }

    static boolean provisioningRequested(String[] args) {
        return containsProvisioning(System.getenv("SPRING_PROFILES_ACTIVE"))
                || containsProvisioning(System.getProperty("spring.profiles.active"))
                || Arrays.stream(args)
                        .filter(argument -> argument.startsWith("--spring.profiles.active="))
                        .map(argument -> argument.substring(argument.indexOf('=') + 1))
                        .anyMatch(AgendaFacilProApplication::containsProvisioning);
    }

    private static boolean containsProvisioning(String profiles) {
        return profiles != null && Arrays.stream(profiles.split(","))
                .map(String::trim)
                .anyMatch("provisioning"::equals);
    }
}
