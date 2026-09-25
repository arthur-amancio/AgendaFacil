package br.com.agendafacilpro.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agendafacilpro.domain.AppUser;
import br.com.agendafacilpro.domain.Appointment;
import br.com.agendafacilpro.domain.AppointmentRules;
import br.com.agendafacilpro.domain.AppointmentStatus;
import br.com.agendafacilpro.domain.Customer;
import br.com.agendafacilpro.domain.Establishment;
import br.com.agendafacilpro.domain.EstablishmentSettings;
import br.com.agendafacilpro.domain.Professional;
import br.com.agendafacilpro.domain.ServiceItem;
import br.com.agendafacilpro.repo.AppointmentRepo;
import br.com.agendafacilpro.repo.CustomerRepo;
import br.com.agendafacilpro.repo.ProfessionalRepo;
import br.com.agendafacilpro.repo.ServiceItemRepo;
import br.com.agendafacilpro.repo.TimeBlockRepo;
import br.com.agendafacilpro.ui.AppointmentViewUtil;
import br.com.agendafacilpro.util.PhoneNormalizer;

@Service
public class AppointmentService {

    private static final String OVERLAP_CONSTRAINT = "ex_appointments_no_blocking_overlap";
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final AppointmentRepo appointments;
    private final CustomerRepo customers;
    private final ServiceItemRepo services;
    private final ProfessionalRepo professionals;
    private final TimeBlockRepo blocks;
    private final BookingGuardService guard;
    private final AppointmentViewUtil view;
    private final AppointmentAuditService audit;
    private final EstablishmentSettingsService settingsService;
    private final BusinessHoursService businessHours;
    private final Clock clock;

    public AppointmentService(AppointmentRepo a, CustomerRepo c, ServiceItemRepo s, ProfessionalRepo p, TimeBlockRepo b, BookingGuardService g, AppointmentViewUtil v, AppointmentAuditService audit, EstablishmentSettingsService settingsService, BusinessHoursService businessHours, Clock clock) {
        appointments = a;
        customers = c;
        services = s;
        professionals = p;
        blocks = b;
        guard = g;
        view = v;
        this.audit = audit;
        this.settingsService = settingsService;
        this.businessHours = businessHours;
        this.clock = clock;
    }

    public enum SlotReason {
        AVAILABLE("Disponível"),
        PAST("Horário já passou"),
        CONFLICT("Indisponível no momento"),
        DOES_NOT_FIT("Esse serviço não cabe nesse horário"),
        BLOCKED("Horário bloqueado pelo estabelecimento"),
        CLOSED("Fora do horário de atendimento"),
        PROFESSIONAL_UNAVAILABLE("Profissional indisponível");

        private final String label;

        SlotReason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Slot(LocalTime start, LocalTime end, boolean available, SlotReason reasonCode, String reasonLabel, String suggestionText) {

    }

    public record Summary(String customer, String establishment, String establishmentSlug, String service, String professional, LocalDateTime start, LocalDateTime end, String status, String statusMessage, String nextStep, String whatsappUrl) {

    }

    private record BookingTarget(ServiceItem serviceItem, Professional professional) {

    }

    /**
     * Lista horários públicos depois de expirar pendências antigas, evitando falsos bloqueios.
     */
    @Transactional
    public List<Slot> slots(Long est, Long serviceId, Long professionalId, LocalDate date) {
        expire(est);
        ServiceItem serviceItem = services.findByIdAndEstablishmentId(serviceId, est).orElseThrow();
        Professional professional = professionals.findByIdAndEstablishmentId(professionalId, est).orElseThrow();
        List<Slot> out = new ArrayList<>();
        var dailyHours = businessHours.hours(est, date).orElse(null);
        for (LocalTime t : businessHours.starts(dailyHours)) {
            LocalDateTime start = LocalDateTime.of(date, t);
            LocalDateTime end = start.plusMinutes(serviceItem.getDurationMinutes());
            SlotReason reason = slotReason(est, professional, serviceItem, start, end);
            out.add(new Slot(t, end.toLocalTime(), reason == SlotReason.AVAILABLE, reason, reason.label(), suggestionFor(est, serviceItem, professional, start, reason)));
        }
        return out;
    }

    /**
     * Revalida um horário escolhido no front-end sem salvar agendamento.
     */
    @Transactional
    public void validatePublicSlot(Establishment est, Long serviceId, Long professionalId, LocalDate date, LocalTime time) {
        expire(est.getId());
        BookingTarget target = bookingTarget(est.getId(), serviceId, professionalId);
        LocalDateTime start = LocalDateTime.of(date, time);
        LocalDateTime end = start.plusMinutes(target.serviceItem().getDurationMinutes());
        validateSubmittedSlot(est.getId(), target.professional(), target.serviceItem(), start, end);
    }

    /**
     * Cria solicitação pública sem conta do cliente e aplica as configurações antifraude do estabelecimento.
     */
    @Transactional
    public Appointment create(Establishment est, Long serviceId, Long professionalId, LocalDate date, LocalTime time, String name, String phone, String ip, String honeypot) {
        if (name == null || name.trim().length() < 2) {
            throw new IllegalArgumentException("Informe seu nome para o estabelecimento identificar sua reserva.");
        }
        EstablishmentSettings settings = settingsService.forEstablishment(est);
        expire(est.getId(), settings);
        BookingGuardService.Decision decision = guard.check(est, phone, ip, honeypot);
        if (!decision.allowed()) {
            throw new IllegalArgumentException(decision.message());
        }
        BookingTarget target = bookingTarget(est.getId(), serviceId, professionalId);
        ServiceItem serviceItem = target.serviceItem();
        Professional professional = target.professional();
        LocalDateTime start = LocalDateTime.of(date, time);
        LocalDateTime end = start.plusMinutes(serviceItem.getDurationMinutes());
        validateSubmittedSlot(est.getId(), professional, serviceItem, start, end);
        if (appointments.countFutureByPhone(est.getId(), decision.normalizedPhone(), AppointmentRules.blockingStatuses(), LocalDateTime.now(clock)) >= settings.getMaxFutureAppointmentsPerPhone()) {
            throw new IllegalArgumentException("Para marcar um novo horário, fale com o estabelecimento.");
        }

        Optional<Customer> existing = customers.findByEstablishmentIdAndPhoneNormalized(est.getId(), decision.normalizedPhone());
        boolean isNew = existing.isEmpty();
        Customer customer = existing.orElseGet(Customer::new);
        customer.setEstablishment(est);
        customer.setName(name.trim());
        customer.setPhoneNormalized(decision.normalizedPhone());
        if (customer.isBlocked() || customer.getNoShowCount() >= settings.getNoShowCountForBlock()) {
            throw new IllegalArgumentException("Para marcar um novo horário, fale com o estabelecimento.");
        }
        customer = customers.save(customer);

        Appointment appointment = new Appointment();
        appointment.setEstablishment(est);
        appointment.setCustomer(customer);
        appointment.setServiceItem(serviceItem);
        appointment.setProfessional(professional);
        appointment.setStartAt(start);
        appointment.setEndAt(end);
        appointment.setPublicToken(newPublicToken());
        appointment.setClientIp(ip);
        appointment.setStatus(requiresApproval(isNew, customer, serviceItem, settings) ? AppointmentStatus.PENDING_APPROVAL : AppointmentStatus.CONFIRMED);
        initializeBookingTimestamps(appointment);
        return saveBooking(appointment);
    }

    /**
     * Cria agendamento feito pelo dono no painel. Sempre grava como confirmado,
     * reutiliza cliente pelo telefone normalizado e aplica a mesma regra de conflito da agenda publica.
     */
    @Transactional
    public Appointment createManual(Establishment est, AppUser user, ManualAppointmentRequest request) {
        if (request.customerName() == null || request.customerName().trim().length() < 2) {
            throw new IllegalArgumentException("Informe o nome do cliente.");
        }
        expire(est.getId());
        String normalizedPhone = PhoneNormalizer.normalize(request.customerPhone());
        BookingTarget target = bookingTarget(est.getId(), request.serviceId(), request.professionalId());
        ServiceItem serviceItem = target.serviceItem();
        Professional professional = target.professional();
        LocalDateTime start = LocalDateTime.of(request.date(), request.time());
        LocalDateTime end = start.plusMinutes(serviceItem.getDurationMinutes());
        validateSubmittedSlot(est.getId(), professional, serviceItem, start, end);

        Optional<Customer> existingCustomer = customers.findByEstablishmentIdAndPhoneNormalized(est.getId(), normalizedPhone);
        Customer customer = existingCustomer.orElseGet(Customer::new);
        customer.setEstablishment(est);
        customer.setName(request.customerName().trim());
        customer.setPhoneNormalized(normalizedPhone);
        if (customer.isBlocked() && !request.forceBlockedCustomer()) {
            throw new IllegalArgumentException("Cliente bloqueado. Confirme que deseja reservar manualmente mesmo assim.");
        }
        customer = customers.save(customer);

        Appointment appointment = new Appointment();
        appointment.setEstablishment(est);
        appointment.setCustomer(customer);
        appointment.setServiceItem(serviceItem);
        appointment.setProfessional(professional);
        appointment.setStartAt(start);
        appointment.setEndAt(end);
        appointment.setPublicToken(newPublicToken());
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setClientIp("manual");
        appointment.setInternalNote(blankToNull(request.internalNote()));
        initializeBookingTimestamps(appointment);
        appointment = saveBooking(appointment);

        audit.record(appointment, user, "MANUAL_CREATE", manualAuditDetails(existingCustomer.isPresent(), customer));
        return appointment;
    }

    @Transactional(readOnly = true)
    public Summary summary(Establishment est, String publicToken) {
        Appointment appointment = appointments.findByEstablishmentIdAndPublicToken(est.getId(), publicToken)
                .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado."));
        String text = "Olá! Fiz uma solicitação de agendamento para " + appointment.getServiceItem().getName() + " com " + appointment.getProfessional().getName() + ".";
        String url = est.getWhatsapp() == null || est.getWhatsapp().isBlank()
                ? null
                : "https://wa.me/" + est.getWhatsapp() + "?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        String statusMessage = appointment.getStatus() == AppointmentStatus.CONFIRMED
                ? "Seu horário foi confirmado."
                : "Seu pedido foi recebido e aguarda confirmação do estabelecimento.";
        String nextStep = appointment.getStatus() == AppointmentStatus.CONFIRMED
                ? "Se precisar falar com o estabelecimento, use o botão do WhatsApp."
                : "O estabelecimento vai revisar seu pedido. Você pode chamar no WhatsApp se quiser acompanhar.";
        return new Summary(appointment.getCustomer().getName(), est.getName(), est.getSlug(), appointment.getServiceItem().getName(), appointment.getProfessional().getName(), appointment.getStartAt(), appointment.getEndAt(), view.statusLabel(appointment.getStatus()), statusMessage, nextStep, url);
    }

    @Transactional(readOnly = true)
    public long countAppointmentsForService(Long establishmentId, Long serviceId) {
        return appointments.countByEstablishmentIdAndServiceItemId(establishmentId, serviceId);
    }

    @Transactional(readOnly = true)
    public long countAppointmentsForProfessional(Long establishmentId, Long professionalId) {
        return appointments.countByEstablishmentIdAndProfessionalId(establishmentId, professionalId);
    }

    /**
     * Aprova uma reserva pendente após revalidar conflito, porque o horário pode ter mudado desde a solicitação.
     */
    @Transactional
    public void approve(Long id, Long est) {
        approve(id, est, null);
    }

    @Transactional
    public void approve(Long id, Long est, AppUser user) {
        Appointment appointment = owned(id, est);
        if (appointment.getStatus() != AppointmentStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Essa reserva não está pendente.");
        }
        EstablishmentSettings settings = settingsService.forEstablishmentId(est);
        if (isExpiredPending(appointment, settings, LocalDateTime.now(clock))) {
            appointment.setStatus(AppointmentStatus.EXPIRED);
            appointment.setCancellationReason("Reserva pendente expirou antes da aprovação");
            throw new IllegalStateException("Essa reserva pendente expirou antes da aprovação. O horário voltou a ficar disponível.");
        }
        noConflict(est, appointment.getProfessional().getId(), appointment.getStartAt(), appointment.getEndAt(), appointment.getId());
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setApprovedAt(LocalDateTime.now(clock));
        recordAudit(appointment, user, "APPROVE", "Reserva aprovada pelo painel");
    }

    /**
     * Rejeitar uma pendência encerra a solicitação e libera o horário para novas reservas.
     */
    @Transactional
    public void reject(Long id, Long est) {
        reject(id, est, null);
    }

    @Transactional
    public void reject(Long id, Long est, AppUser user) {
        Appointment appointment = owned(id, est);
        if (appointment.getStatus() != AppointmentStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Somente reservas pendentes podem ser recusadas.");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason("Recusado pelo estabelecimento");
        recordAudit(appointment, user, "REJECT", "Reserva recusada; horário liberado");
    }

    /**
     * Cancelamento administrativo mantém histórico, mas remove o bloqueio do horário.
     */
    @Transactional
    public void cancel(Long id, Long est, String reason) {
        cancel(id, est, reason, null);
    }

    @Transactional
    public void cancel(Long id, Long est, String reason, AppUser user) {
        Appointment appointment = owned(id, est);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED && appointment.getStatus() != AppointmentStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Esse agendamento ja esta encerrado.");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason(reason == null || reason.isBlank() ? "Cancelado pelo estabelecimento" : reason.trim());
        recordAudit(appointment, user, "CANCEL", "Agendamento cancelado; horário liberado");
    }

    @Transactional
    public void complete(Long id, Long est) {
        complete(id, est, null);
    }

    @Transactional
    public void complete(Long id, Long est, AppUser user) {
        Appointment appointment = owned(id, est);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Somente confirmados podem ser concluídos.");
        }
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment.setCompletedAt(LocalDateTime.now(clock));
        recordAudit(appointment, user, "COMPLETE", "Atendimento concluido pelo painel");
    }

    /**
     * Registra falta e incrementa o contador usado para regras futuras configuráveis.
     */
    @Transactional
    public void noShow(Long id, Long est) {
        noShow(id, est, null);
    }

    @Transactional
    public void noShow(Long id, Long est, AppUser user) {
        Appointment appointment = owned(id, est);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Somente confirmados podem receber falta.");
        }
        appointment.setStatus(AppointmentStatus.NO_SHOW);
        appointment.getCustomer().addNoShow();
        recordAudit(appointment, user, "NO_SHOW", "Falta registrada no painel");
    }

    /**
     * Expira pendências por início passado ou pelo tempo configurado, liberando o horário.
     */
    @Transactional
    public void expire(Long est) {
        expire(est, settingsService.forEstablishmentId(est));
    }

    @Transactional
    public void expire(Long est, EstablishmentSettings settings) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Appointment> expired = new ArrayList<>();
        expired.addAll(appointments.findByEstablishmentIdAndStatusAndStartAtBefore(est, AppointmentStatus.PENDING_APPROVAL, now));
        expired.addAll(appointments.findByEstablishmentIdAndStatusAndCreatedAtBefore(est, AppointmentStatus.PENDING_APPROVAL, now.minusMinutes(settings.getPendingExpirationMinutes())));
        List<Appointment> distinctExpired = expired.stream().distinct().toList();
        distinctExpired.forEach(appointment -> {
            appointment.setStatus(AppointmentStatus.EXPIRED);
            appointment.setCancellationReason("Reserva pendente expirou automaticamente");
        });
        if (!distinctExpired.isEmpty()) {
            appointments.flush();
        }
    }

    private Appointment owned(Long id, Long est) {
        return appointments.findByIdAndEstablishmentId(id, est)
                .orElseThrow(() -> new IllegalArgumentException("Agendamento não encontrado para este estabelecimento."));
    }

    private BookingTarget bookingTarget(Long est, Long serviceId, Long professionalId) {
        ServiceItem serviceItem = services.findByIdAndEstablishmentId(serviceId, est)
                .filter(ServiceItem::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Serviço indisponível."));
        Professional professional = professionals.findByIdAndEstablishmentId(professionalId, est)
                .filter(Professional::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Profissional indisponível."));
        if (!professional.performs(serviceItem) && !professionals.existsActiveQualified(est, professionalId, serviceId)) {
            throw new IllegalArgumentException("Esse profissional não realiza o serviço escolhido.");
        }
        return new BookingTarget(serviceItem, professional);
    }

    private void validateSubmittedSlot(Long est, Professional professional, ServiceItem serviceItem, LocalDateTime start, LocalDateTime end) {
        SlotReason reason = slotReason(est, professional, serviceItem, start, end);
        if (reason != SlotReason.AVAILABLE) {
            throw new IllegalArgumentException("Esse horário não está disponível para este serviço.");
        }
    }

    private SlotReason slotReason(Long est, Professional professional, ServiceItem serviceItem, LocalDateTime start, LocalDateTime end) {
        if (!professional.isActive() || !professional.performs(serviceItem) && !professionals.existsActiveQualified(est, professional.getId(), serviceItem.getId())) {
            return SlotReason.PROFESSIONAL_UNAVAILABLE;
        }
        var dailyHours = businessHours.hours(est, start.toLocalDate()).orElse(null);
        if (dailyHours == null || !dailyHours.isOpen()) {
            return SlotReason.CLOSED;
        }
        if (!businessHours.fits(dailyHours, start, end)) {
            return SlotReason.DOES_NOT_FIT;
        }
        if (!start.isAfter(LocalDateTime.now(clock).minusMinutes(1))) {
            return SlotReason.PAST;
        }
        if (blocks.existsOverlap(est, professional.getId(), start, end)) {
            return SlotReason.BLOCKED;
        }
        if (appointments.existsBlockingOverlap(est, professional.getId(), start, end, AppointmentRules.blockingStatuses(), null)) {
            return SlotReason.CONFLICT;
        }
        return SlotReason.AVAILABLE;
    }

    private String suggestionFor(Long est, ServiceItem serviceItem, Professional professional, LocalDateTime start, SlotReason reason) {
        if (reason != SlotReason.CONFLICT && reason != SlotReason.DOES_NOT_FIT) {
            return null;
        }
        List<String> suggestions = new ArrayList<>();
        List<String> nextTimes = new ArrayList<>();
        var dailyHours = businessHours.hours(est, start.toLocalDate()).orElse(null);
        List<LocalTime> dailyStarts = businessHours.starts(dailyHours);
        for (LocalTime next : dailyStarts.stream().filter(t -> t.isAfter(start.toLocalTime())).toList()) {
            if (nextTimes.size() >= 3) break;
            LocalDateTime candidateStart = LocalDateTime.of(start.toLocalDate(), next);
            LocalDateTime candidateEnd = candidateStart.plusMinutes(serviceItem.getDurationMinutes());
            if (slotReason(est, professional, serviceItem, candidateStart, candidateEnd) == SlotReason.AVAILABLE) {
                nextTimes.add(next.toString());
            }
        }
        if (!nextTimes.isEmpty()) {
            suggestions.add("Tente " + String.join(", ", nextTimes) + " com " + professional.getName() + ".");
        }

        List<String> otherProfessionals = professionals.findActiveQualified(est, serviceItem.getId()).stream()
                .filter(candidate -> !candidate.getId().equals(professional.getId()))
                .filter(candidate -> slotReason(est, candidate, serviceItem, start, start.plusMinutes(serviceItem.getDurationMinutes())) == SlotReason.AVAILABLE)
                .limit(2)
                .map(Professional::getName)
                .toList();
        if (!otherProfessionals.isEmpty()) {
            suggestions.add("Também há outro profissional disponível nesse horário: " + String.join(", ", otherProfessionals) + ".");
        }
        if (suggestions.isEmpty()) {
            return "Não encontramos outro horário próximo neste dia.";
        }
        return String.join(" ", suggestions);
    }

    private void noConflict(Long est, Long prof, LocalDateTime start, LocalDateTime end, Long ignore) {
        if (blocks.existsOverlap(est, prof, start, end)) {
            throw new IllegalArgumentException("Esse horário foi bloqueado pelo estabelecimento.");
        }
        if (appointments.existsBlockingOverlap(est, prof, start, end, AppointmentRules.blockingStatuses(), ignore)) {
            throw new IllegalArgumentException("Esse horário não está mais disponível.");
        }
    }

    private void recordAudit(Appointment appointment, AppUser user, String action, String details) {
        if (user != null) {
            audit.record(appointment, user, action, details);
        }
    }

    private String manualAuditDetails(boolean reusedCustomer, Customer customer) {
        List<String> details = new ArrayList<>();
        details.add(reusedCustomer ? "Cliente reutilizado" : "Cliente criado");
        if (customer.isBlocked()) {
            details.add("cliente bloqueado confirmado pelo dono");
        }
        if (customer.getNoShowCount() >= 2) {
            details.add("cliente com histórico de faltas");
        }
        return String.join("; ", details);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean requiresApproval(boolean isNew, Customer customer, ServiceItem serviceItem, EstablishmentSettings settings) {
        return (isNew && settings.isNewClientRequiresApproval())
                || customer.getNoShowCount() >= settings.getNoShowCountForManualApproval()
                || serviceItem.getDurationMinutes() > settings.getLongServiceManualApprovalMinutes();
    }

    private boolean isExpiredPending(Appointment appointment, EstablishmentSettings settings, LocalDateTime now) {
        return !appointment.getStartAt().isAfter(now)
                || appointment.getCreatedAt().plusMinutes(settings.getPendingExpirationMinutes()).isBefore(now);
    }

    private Appointment saveBooking(Appointment appointment) {
        try {
            return appointments.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            if (isOverlapViolation(ex)) {
                throw new BookingConflictException(ex);
            }
            throw ex;
        }
    }

    private void initializeBookingTimestamps(Appointment appointment) {
        LocalDateTime now = LocalDateTime.now(clock);
        appointment.setCreatedAt(now);
        appointment.setUpdatedAt(now);
    }

    private boolean isOverlapViolation(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && "23P01".equals(sqlException.getSQLState())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains(OVERLAP_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String newPublicToken() {
        for (int i = 0; i < 10; i++) {
            byte[] bytes = new byte[18];
            TOKEN_RANDOM.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (!appointments.existsByPublicToken(token)) {
                return token;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um identificador seguro para o agendamento.");
    }
}
