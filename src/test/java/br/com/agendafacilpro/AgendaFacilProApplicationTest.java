package br.com.agendafacilpro;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgendaFacilProApplicationTest {
    @Test
    void detectsProvisioningProfileFromExplicitCommandLineProfiles() {
        assertThat(AgendaFacilProApplication.provisioningRequested(
                new String[] {"--spring.profiles.active=prod,provisioning"})).isTrue();
    }

    @Test
    void ordinaryExplicitProfilesDoNotSelectProvisioningMode() {
        assertThat(AgendaFacilProApplication.provisioningRequested(
                new String[] {"--spring.profiles.active=prod"})).isFalse();
    }

    @Test
    void invalidProvisioningProfileStillSelectsNonWebModeBeforeStartupIsRejected() {
        assertThat(AgendaFacilProApplication.provisioningRequested(
                new String[] {"--spring.profiles.active=dev,provisioning"})).isTrue();
    }
}
