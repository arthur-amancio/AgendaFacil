package br.com.agendafacilpro.provisioning;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("prod & provisioning")
@EnableConfigurationProperties(ProvisioningProperties.class)
public class ProvisioningConfiguration {
}
