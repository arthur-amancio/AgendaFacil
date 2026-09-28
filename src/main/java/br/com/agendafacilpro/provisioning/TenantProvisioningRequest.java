package br.com.agendafacilpro.provisioning;

public record TenantProvisioningRequest(
        String establishmentName,
        String slug,
        String whatsapp,
        String city,
        String description,
        String ownerName,
        String ownerEmail) {
}
