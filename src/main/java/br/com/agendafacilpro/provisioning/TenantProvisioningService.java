package br.com.agendafacilpro.provisioning;

import java.nio.CharBuffer;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agendafacilpro.domain.AppUser;
import br.com.agendafacilpro.domain.Establishment;
import br.com.agendafacilpro.domain.EstablishmentBusinessHours;
import br.com.agendafacilpro.domain.EstablishmentSettings;
import br.com.agendafacilpro.domain.UserRole;
import br.com.agendafacilpro.repo.EstablishmentBusinessHoursRepo;
import br.com.agendafacilpro.repo.EstablishmentRepo;
import br.com.agendafacilpro.repo.EstablishmentSettingsRepo;
import br.com.agendafacilpro.repo.ProfessionalRepo;
import br.com.agendafacilpro.repo.ServiceItemRepo;
import br.com.agendafacilpro.repo.UserRepo;
import jakarta.persistence.EntityManager;

@Service
public class TenantProvisioningService {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_INPUT = Pattern.compile("[0-9()\\s+\\-.]+");
    private static final Set<String> RESERVED_SLUGS = Set.of(
            "agenda-demo", "admin", "api", "login", "logout", "panel", "demo");

    private final EstablishmentRepo establishments;
    private final EstablishmentSettingsRepo settings;
    private final EstablishmentBusinessHoursRepo businessHours;
    private final UserRepo users;
    private final ServiceItemRepo services;
    private final ProfessionalRepo professionals;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final Clock clock;

    public TenantProvisioningService(
            EstablishmentRepo establishments,
            EstablishmentSettingsRepo settings,
            EstablishmentBusinessHoursRepo businessHours,
            UserRepo users,
            ServiceItemRepo services,
            ProfessionalRepo professionals,
            PasswordEncoder passwordEncoder,
            EntityManager entityManager,
            Clock clock) {
        this.establishments = establishments;
        this.settings = settings;
        this.businessHours = businessHours;
        this.users = users;
        this.services = services;
        this.professionals = professionals;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public TenantProvisioningResult create(TenantProvisioningRequest request, char[] rawCredential) {
        TenantProvisioningRequest normalized = normalizeAndValidate(request);
        validateCredential(rawCredential);

        if (establishments.existsBySlug(normalized.slug())) {
            throw new ProvisioningException("Já existe um estabelecimento com esse slug. Nenhum dado foi alterado.");
        }
        if (users.existsByEmailIgnoreCase(normalized.ownerEmail())) {
            throw new ProvisioningException("Já existe um usuário com esse e-mail. Nenhum dado foi alterado.");
        }

        Establishment establishment = new Establishment();
        establishment.setName(normalized.establishmentName());
        establishment.setSlug(normalized.slug());
        establishment.setWhatsapp(normalized.whatsapp());
        establishment.setCity(normalized.city());
        establishment.setDescription(normalized.description());
        establishment.setActive(false);
        establishment.setCreatedAt(LocalDateTime.now(clock));
        establishments.save(establishment);

        settings.save(EstablishmentSettings.defaultsFor(establishment));
        businessHours.saveAll(Arrays.stream(DayOfWeek.values())
                .map(day -> closedDay(establishment, day))
                .toList());

        AppUser owner = new AppUser();
        owner.setEstablishment(establishment);
        owner.setName(normalized.ownerName());
        owner.setEmail(normalized.ownerEmail());
        owner.setPasswordHash(passwordEncoder.encode(CharBuffer.wrap(rawCredential)));
        owner.setRole(UserRole.OWNER);
        owner.setEnabled(true);
        users.save(owner);

        entityManager.flush();
        return new TenantProvisioningResult(establishment.getId(), establishment.getSlug(), owner.getId());
    }

    @Transactional
    public TenantActivationResult activate(String requestedSlug) {
        String slug = validateSlug(requireText(requestedSlug, "Informe o slug do estabelecimento."));
        Establishment establishment = establishments.findForUpdateBySlug(slug)
                .orElseThrow(() -> new ProvisioningException("Estabelecimento não encontrado."));
        if (establishment.isActive()) {
            throw new ProvisioningException("O estabelecimento já está ativo. Nenhum dado foi alterado.");
        }

        Long tenantId = establishment.getId();
        if (!settings.existsByEstablishmentId(tenantId)) {
            throw new ProvisioningException("Ativação bloqueada: configurações do estabelecimento estão ausentes.");
        }

        List<EstablishmentBusinessHours> weekly = businessHours.findByEstablishmentIdOrderByDayOfWeek(tenantId);
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        weekly.forEach(day -> days.add(day.getDayOfWeek()));
        if (weekly.size() != 7 || days.size() != 7 || !days.containsAll(EnumSet.allOf(DayOfWeek.class))) {
            throw new ProvisioningException("Ativação bloqueada: os sete dias de funcionamento precisam estar configurados.");
        }
        boolean hasValidOpenDay = weekly.stream().anyMatch(this::isValidOpenDay);
        if (!hasValidOpenDay) {
            throw new ProvisioningException("Ativação bloqueada: configure pelo menos um dia aberto e válido.");
        }
        if (!users.existsByEstablishmentIdAndRoleAndEnabledTrue(tenantId, UserRole.OWNER)) {
            throw new ProvisioningException("Ativação bloqueada: nenhum OWNER habilitado foi encontrado.");
        }
        if (!services.existsByEstablishmentIdAndActiveTrue(tenantId)) {
            throw new ProvisioningException("Ativação bloqueada: cadastre pelo menos um serviço ativo.");
        }
        if (!professionals.existsByEstablishmentIdAndActiveTrue(tenantId)) {
            throw new ProvisioningException("Ativação bloqueada: cadastre pelo menos um profissional ativo.");
        }
        if (!professionals.existsAnyActiveQualified(tenantId)) {
            throw new ProvisioningException("Ativação bloqueada: vincule um profissional ativo a um serviço ativo.");
        }

        establishment.setActive(true);
        establishments.save(establishment);
        entityManager.flush();
        return new TenantActivationResult(tenantId, slug);
    }

    TenantProvisioningRequest normalizeAndValidate(TenantProvisioningRequest request) {
        if (request == null) {
            throw new ProvisioningException("Informe os dados do estabelecimento e do OWNER.");
        }
        String name = bounded(requireText(request.establishmentName(), "Informe o nome do estabelecimento."), 120, "Nome do estabelecimento");
        String slug = validateSlug(requireText(request.slug(), "Informe o slug explicitamente."));
        String whatsapp = normalizeWhatsapp(request.whatsapp());
        String city = optionalBounded(request.city(), 80, "Cidade");
        String description = optionalBounded(request.description(), 2_000, "Descrição");
        String ownerName = bounded(requireText(request.ownerName(), "Informe o nome do OWNER."), 120, "Nome do OWNER");
        String ownerEmail = requireText(request.ownerEmail(), "Informe o e-mail do OWNER.").toLowerCase(Locale.ROOT);
        if (ownerEmail.length() > 160 || !EMAIL.matcher(ownerEmail).matches()) {
            throw new ProvisioningException("Informe um e-mail válido para o OWNER.");
        }
        return new TenantProvisioningRequest(name, slug, whatsapp, city, description, ownerName, ownerEmail);
    }

    private String validateSlug(String slug) {
        if (slug.length() < 3 || slug.length() > 80 || !SLUG.matcher(slug).matches()) {
            throw new ProvisioningException("O slug deve ter 3 a 80 caracteres, somente lowercase, números e hífens simples.");
        }
        if (RESERVED_SLUGS.contains(slug)) {
            throw new ProvisioningException("Esse slug é reservado e não pode ser usado.");
        }
        return slug;
    }

    private String normalizeWhatsapp(String raw) {
        String input = requireText(raw, "Informe o WhatsApp do estabelecimento.");
        if (!PHONE_INPUT.matcher(input).matches()) {
            throw new ProvisioningException("Informe um WhatsApp brasileiro válido.");
        }
        String digits = input.replaceAll("\\D", "");
        if (digits.length() == 10 || digits.length() == 11) {
            digits = "55" + digits;
        }
        if ((digits.length() != 12 && digits.length() != 13) || !digits.startsWith("55")) {
            throw new ProvisioningException("Informe um WhatsApp brasileiro válido, com DDD.");
        }
        return digits;
    }

    private void validateCredential(char[] credential) {
        if (credential == null || credential.length < 32) {
            throw new ProvisioningException("A credencial inicial não atende ao requisito mínimo de segurança.");
        }
    }

    private EstablishmentBusinessHours closedDay(Establishment establishment, DayOfWeek day) {
        EstablishmentBusinessHours hours = new EstablishmentBusinessHours();
        hours.setEstablishment(establishment);
        hours.setDayOfWeek(day);
        hours.setOpen(false);
        hours.setOpeningTime(null);
        hours.setClosingTime(null);
        return hours;
    }

    private boolean isValidOpenDay(EstablishmentBusinessHours day) {
        return day.isOpen()
                && day.getOpeningTime() != null
                && day.getClosingTime() != null
                && day.getOpeningTime().isBefore(day.getClosingTime());
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ProvisioningException(message);
        }
        return value.trim();
    }

    private String bounded(String value, int max, String label) {
        if (value.length() > max) {
            throw new ProvisioningException(label + " excede o limite de " + max + " caracteres.");
        }
        return value;
    }

    private String optionalBounded(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return bounded(value.trim(), max, label);
    }
}
