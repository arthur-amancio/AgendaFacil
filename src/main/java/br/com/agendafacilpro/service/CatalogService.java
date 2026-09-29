package br.com.agendafacilpro.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agendafacilpro.domain.Establishment;
import br.com.agendafacilpro.domain.Professional;
import br.com.agendafacilpro.domain.ServiceItem;
import br.com.agendafacilpro.repo.EstablishmentRepo;
import br.com.agendafacilpro.repo.ProfessionalRepo;
import br.com.agendafacilpro.repo.ServiceItemRepo;

@Service
public class CatalogService {

    private final EstablishmentRepo establishments;
    private final ServiceItemRepo services;
    private final ProfessionalRepo professionals;

    public CatalogService(EstablishmentRepo e, ServiceItemRepo s, ProfessionalRepo p) {
        establishments = e;
        services = s;
        professionals = p;
    }

    @Transactional(readOnly = true)
    public Establishment establishment(String slug) {
        return establishments.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Esse link de agendamento não está disponível."));
    }

    @Transactional(readOnly = true)
    public List<ServiceItem> services(Long est) {
        return services.findByEstablishmentIdAndActiveTrueOrderBySortOrderAscNameAsc(est);
    }

    @Transactional(readOnly = true)
    public List<Professional> professionals(Long est) {
        return professionals.findByEstablishmentIdAndActiveTrueOrderBySortOrderAscNameAsc(est);
    }

    @Transactional(readOnly = true)
    public List<Professional> professionalsForService(Long est, Long serviceId) {
        service(serviceId, est);
        return professionals.findActiveQualified(est, serviceId);
    }

    @Transactional(readOnly = true)
    public ServiceItem service(Long id, Long est) {
        return services.findByIdAndEstablishmentId(id, est).filter(ServiceItem::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço indisponível."));
    }

    @Transactional(readOnly = true)
    public Professional professional(Long id, Long est) {
        return professionals.findByIdAndEstablishmentId(id, est).filter(Professional::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Profissional indisponível."));
    }

    @Transactional(readOnly = true)
    public Professional professionalForService(Long id, Long serviceId, Long est) {
        ServiceItem service = service(serviceId, est);
        return professionals.findByIdAndEstablishmentId(id, est)
                .filter(Professional::isActive)
                .filter(professional -> professional.performs(service))
                .orElseThrow(() -> new ResourceNotFoundException("Profissional indisponível para o serviço escolhido."));
    }
}
