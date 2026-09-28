package br.com.agendafacilpro.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class TenantProvisioningValidationTest {
    private final TenantProvisioningService service = new TenantProvisioningService(
            null, null, null, null, null, null, null, null, null);

    @Test
    void normalizesEmailAndBrazilianWhatsappWithoutDerivingSlug() {
        TenantProvisioningRequest normalized = service.normalizeAndValidate(request("piloto-real", " Owner@Example.TEST ", "(17) 99999-9999"));

        assertThat(normalized.slug()).isEqualTo("piloto-real");
        assertThat(normalized.ownerEmail()).isEqualTo("owner@example.test");
        assertThat(normalized.whatsapp()).isEqualTo("5517999999999");
    }

    @Test
    void rejectsInvalidAndReservedSlugs() {
        List<String> invalid = List.of(
                "ABCD", "ab", "-piloto", "piloto-", "piloto--real",
                "piloto_real", "agenda-demo", "admin", "api", "login", "logout", "panel", "demo");

        invalid.forEach(slug -> assertThatThrownBy(() -> service.normalizeAndValidate(request(slug, "owner@example.test", "17999999999")))
                .as("slug %s", slug)
                .isInstanceOf(ProvisioningException.class));
    }

    @Test
    void rejectsInvalidEmailAndWhatsapp() {
        assertThatThrownBy(() -> service.normalizeAndValidate(request("piloto-real", "sem-arroba", "17999999999")))
                .hasMessageContaining("e-mail válido");
        assertThatThrownBy(() -> service.normalizeAndValidate(request("piloto-real", "owner@example.test", "telefone 17999999999")))
                .hasMessageContaining("WhatsApp");
    }

    private TenantProvisioningRequest request(String slug, String email, String whatsapp) {
        return new TenantProvisioningRequest(
                "Piloto", slug, whatsapp, null, null, "Owner", email);
    }
}
