package br.com.agendafacilpro.web;

import java.time.LocalDate;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.agendafacilpro.domain.AppUser;
import br.com.agendafacilpro.domain.AppointmentStatus;
import br.com.agendafacilpro.domain.Customer;
import br.com.agendafacilpro.domain.Professional;
import br.com.agendafacilpro.domain.ServiceItem;
import br.com.agendafacilpro.domain.TimeBlock;
import br.com.agendafacilpro.repo.CustomerRepo;
import br.com.agendafacilpro.repo.ProfessionalRepo;
import br.com.agendafacilpro.repo.ServiceItemRepo;
import br.com.agendafacilpro.repo.TimeBlockRepo;
import br.com.agendafacilpro.service.AppointmentService;
import br.com.agendafacilpro.service.BusinessHoursForm;
import br.com.agendafacilpro.service.BusinessHoursService;
import br.com.agendafacilpro.service.CurrentUserService;
import br.com.agendafacilpro.service.DashboardService;
import br.com.agendafacilpro.service.EstablishmentSettingsForm;
import br.com.agendafacilpro.service.EstablishmentSettingsService;
import br.com.agendafacilpro.service.ManualAppointmentRequest;
import br.com.agendafacilpro.util.PhoneNormalizer;
import br.com.agendafacilpro.web.form.ProfessionalForm;
import br.com.agendafacilpro.web.form.ServiceForm;
import br.com.agendafacilpro.web.form.TimeBlockForm;
import br.com.agendafacilpro.web.form.WeeklyBusinessHoursForm;
import jakarta.validation.Valid;

@Controller
public class PanelController {

    private final CurrentUserService current;
    private final DashboardService dashboard;
    private final AppointmentService appointments;
    private final ServiceItemRepo services;
    private final ProfessionalRepo professionals;
    private final TimeBlockRepo blocks;
    private final CustomerRepo customers;
    private final EstablishmentSettingsService settingsService;
    private final BusinessHoursService businessHours;
    private final Clock clock;

    public PanelController(CurrentUserService c, DashboardService d, AppointmentService a, ServiceItemRepo s, ProfessionalRepo p, TimeBlockRepo b, CustomerRepo customers, EstablishmentSettingsService settingsService, BusinessHoursService businessHours, Clock clock) {
        current = c;
        dashboard = d;
        appointments = a;
        services = s;
        professionals = p;
        blocks = b;
        this.customers = customers;
        this.settingsService = settingsService;
        this.businessHours = businessHours;
        this.clock = clock;
    }

    @GetMapping("/panel")
    String panel(Model model) {
        AppUser u = shell(model, "dashboard", "Dashboard", "Resumo inteligente para acompanhar hoje, pendências e próximos horários.");
        Long est = u.getEstablishment().getId();
        model.addAttribute("dashboard", dashboard.data(est));
        model.addAttribute("todayDate", LocalDate.now(clock));
        return "panel/dashboard";
    }

    @GetMapping("/panel/agenda")
    String agenda(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                  @RequestParam(required = false) Long professionalId,
                  @RequestParam(required = false) AppointmentStatus status,
                  Model model) {
        AppUser u = shell(model, "agenda", "Agenda", "Visualize o dia e a semana com filtros por profissional e status.");
        Long est = u.getEstablishment().getId();
        DashboardService.Filter filter = filter(startDate, endDate, professionalId, status);
        model.addAttribute("dashboard", dashboard.data(est, filter));
        model.addAttribute("professionals", professionals.findByEstablishmentIdOrderByActiveDescSortOrderAscNameAsc(est));
        model.addAttribute("statuses", AppointmentStatus.values());
        model.addAttribute("filter", filter);
        model.addAttribute("todayDate", LocalDate.now(clock));
        model.addAttribute("next7Date", LocalDate.now(clock).plusDays(6));
        return "panel/agenda";
    }

    @GetMapping("/panel/appointments")
    String appointments(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                        @RequestParam(required = false) Long professionalId,
                        @RequestParam(required = false) AppointmentStatus status,
                        @RequestParam(required = false) String customerPhone,
                        Model model) {
        AppUser u = shell(model, "appointments", "Agendamentos", "Lista operacional com filtros e ações conforme o status.");
        Long est = u.getEstablishment().getId();
        DashboardService.Filter filter = filter(startDate, endDate, professionalId, status);
        model.addAttribute("dashboard", dashboard.data(est, filter));
        model.addAttribute("activeServices", services.findByEstablishmentIdAndActiveTrueOrderBySortOrderAscNameAsc(est));
        model.addAttribute("activeProfessionals", professionals.findByEstablishmentIdAndActiveTrueOrderBySortOrderAscNameAsc(est));
        model.addAttribute("professionals", professionals.findByEstablishmentIdOrderByActiveDescSortOrderAscNameAsc(est));
        model.addAttribute("statuses", AppointmentStatus.values());
        model.addAttribute("filter", filter);
        model.addAttribute("todayDate", LocalDate.now(clock));
        model.addAttribute("next7Date", LocalDate.now(clock).plusDays(6));
        model.addAttribute("customerLookupPhone", customerPhone);
        model.addAttribute("customerLookup", lookupCustomer(est, customerPhone));
        return "panel/appointments";
    }

    @GetMapping("/panel/customers")
    String customers(@RequestParam(required = false) String q, Model model) {
        AppUser u = shell(model, "customers", "Clientes", "Consulte clientes por nome ou telefone e acompanhe faltas.");
        Long est = u.getEstablishment().getId();
        model.addAttribute("customerQuery", q);
        model.addAttribute("customers", customerList(est, q));
        return "panel/customers";
    }

    @GetMapping("/panel/services")
    String services(@RequestParam(required = false) String q, Model model) {
        AppUser u = shell(model, "services", "Serviços", "Gerencie o catálogo público sem poluir o dashboard.");
        Long est = u.getEstablishment().getId();
        model.addAttribute("serviceQuery", q);
        model.addAttribute("services", serviceList(est, q));
        return "panel/services";
    }

    @GetMapping("/panel/professionals")
    String professionals(@RequestParam(required = false) String q, Model model) {
        AppUser u = shell(model, "professionals", "Profissionais", "Organize equipe, WhatsApp e serviços realizados.");
        Long est = u.getEstablishment().getId();
        model.addAttribute("professionalQuery", q);
        model.addAttribute("professionals", professionalList(est, q));
        model.addAttribute("activeServices", services.findByEstablishmentIdAndActiveTrueOrderBySortOrderAscNameAsc(est));
        return "panel/professionals";
    }

    @GetMapping("/panel/time-blocks")
    String timeBlocks(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        AppUser u = shell(model, "blocks", "Bloqueios", "Reserve períodos em que a agenda online não deve aceitar horários.");
        Long est = u.getEstablishment().getId();
        LocalDate selected = date == null ? LocalDate.now(clock) : date;
        List<TimeBlock> timeBlocks = date == null
                ? blocks.findTop20ByEstablishmentIdOrderByStartAtDesc(est)
                : blocks.findByEstablishmentIdAndStartAtBetweenOrderByStartAtAsc(est, selected.atStartOfDay(), selected.plusDays(1).atStartOfDay());
        model.addAttribute("timeBlocks", timeBlocks);
        model.addAttribute("activeProfessionals", professionals.findByEstablishmentIdAndActiveTrueOrderBySortOrderAscNameAsc(est));
        model.addAttribute("selectedDate", selected);
        return "panel/time-blocks";
    }

    @GetMapping("/panel/settings")
    String settings(Model model) {
        AppUser u = shell(model, "settings", "Configurações", "Ajuste regras do agendamento online e da página pública.");
        model.addAttribute("settings", settingsService.forEstablishment(u.getEstablishment()));
        model.addAttribute("businessHours", businessHours.weekly(u.getEstablishment().getId()));
        return "panel/settings";
    }

    @GetMapping("/panel/reports")
    String reports(Model model) {
        AppUser u = shell(model, "reports", "Relatórios", "Indicadores simples para acompanhar operação e faltas.");
        model.addAttribute("reports", dashboard.reports(u.getEstablishment().getId()));
        return "panel/reports";
    }

    @PostMapping("/panel/appointments/manual")
    String manual(@Valid @ModelAttribute ManualAppointmentRequest request, BindingResult binding, RedirectAttributes r) {
        if (binding.hasErrors()) {
            return panelError(r, bindingMessage(binding), "/panel/appointments");
        }
        try {
            AppUser user = current.user();
            appointments.createManual(user.getEstablishment(), user, request);
            r.addFlashAttribute("success", "Novo agendamento confirmado. Esse horário ficou indisponível na agenda pública.");
            return "redirect:/panel/appointments";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/appointments");
        }
    }

    @PostMapping("/panel/appointments/{id}/approve")
    String approve(@PathVariable Long id, RedirectAttributes r) {
        try {
            appointments.approve(id, current.establishmentId(), current.user());
            r.addFlashAttribute("success", "Reserva aprovada e horário confirmado.");
            return "redirect:/panel/appointments";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/appointments");
        }
    }

    @PostMapping("/panel/appointments/{id}/reject")
    String reject(@PathVariable Long id, RedirectAttributes r) {
        try {
            appointments.reject(id, current.establishmentId(), current.user());
            r.addFlashAttribute("success", "Reserva recusada. O horário voltou a ficar disponível.");
            return "redirect:/panel/appointments";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/appointments");
        }
    }

    @PostMapping("/panel/appointments/{id}/complete")
    String complete(@PathVariable Long id, RedirectAttributes r) {
        try {
            appointments.complete(id, current.establishmentId(), current.user());
            r.addFlashAttribute("success", "Atendimento marcado como concluído.");
            return "redirect:/panel/appointments";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/appointments");
        }
    }

    @PostMapping("/panel/appointments/{id}/no-show")
    String noShow(@PathVariable Long id, RedirectAttributes r) {
        try {
            appointments.noShow(id, current.establishmentId(), current.user());
            r.addFlashAttribute("success", "Falta registrada no histórico do cliente.");
            return "redirect:/panel/appointments";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/appointments");
        }
    }

    @PostMapping("/panel/appointments/{id}/cancel")
    String cancel(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes r) {
        try {
            appointments.cancel(id, current.establishmentId(), reason, current.user());
            r.addFlashAttribute("success", "Agendamento cancelado sem apagar o histórico.");
            return "redirect:/panel/appointments";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/appointments");
        }
    }

    @PostMapping("/panel/services")
    String service(@Valid @ModelAttribute ServiceForm form, BindingResult binding, RedirectAttributes r) {
        if (binding.hasErrors()) {
            return panelError(r, bindingMessage(binding), "/panel/services");
        }
        try {
            AppUser u = current.user();
            ServiceItem s = new ServiceItem();
            s.setEstablishment(u.getEstablishment());
            applyServiceFields(s, form);
            services.save(s);
            r.addFlashAttribute("success", "Serviço salvo com sucesso.");
            return "redirect:/panel/services";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/services");
        }
    }

    @PostMapping("/panel/services/{id}")
    String updateService(@PathVariable Long id, @Valid @ModelAttribute ServiceForm form, BindingResult binding, RedirectAttributes r) {
        if (binding.hasErrors()) {
            return panelError(r, bindingMessage(binding), "/panel/services");
        }
        try {
            ServiceItem s = services.findByIdAndEstablishmentId(id, current.establishmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado para este estabelecimento."));
            applyServiceFields(s, form);
            s.setActive(form.active());
            services.save(s);
            r.addFlashAttribute("success", "Serviço atualizado com sucesso.");
            return "redirect:/panel/services";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/services");
        }
    }

    @PostMapping("/panel/services/{id}/toggle")
    String toggleService(@PathVariable Long id, RedirectAttributes r) {
        try {
            ServiceItem s = services.findByIdAndEstablishmentId(id, current.establishmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado para este estabelecimento."));
            s.setActive(!s.isActive());
            services.save(s);
            r.addFlashAttribute("success", s.isActive() ? "Serviço reativado." : "Serviço arquivado.");
            return "redirect:/panel/services";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/services");
        }
    }

    @PostMapping("/panel/services/{id}/delete")
    String deleteService(@PathVariable Long id, RedirectAttributes r) {
        try {
            Long est = current.establishmentId();
            ServiceItem s = services.findByIdAndEstablishmentId(id, est)
                    .orElseThrow(() -> new IllegalArgumentException("Serviço não encontrado para este estabelecimento."));
            if (appointmentsCountByService(est, id) > 0) {
                s.setActive(false);
                services.save(s);
                r.addFlashAttribute("error", "Este item já possui histórico e foi arquivado.");
            } else {
                services.delete(s);
                r.addFlashAttribute("success", "Serviço excluído com sucesso.");
            }
            return "redirect:/panel/services";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/services");
        }
    }

    @PostMapping("/panel/professionals")
    String professional(@Valid @ModelAttribute ProfessionalForm form, BindingResult binding, RedirectAttributes r) {
        if (binding.hasErrors()) {
            return panelError(r, bindingMessage(binding), "/panel/professionals");
        }
        try {
            AppUser u = current.user();
            Professional p = new Professional();
            p.setEstablishment(u.getEstablishment());
            applyProfessionalFields(p, form.name(), form.bio(), form.whatsapp(), true, form.serviceIds(), u.getEstablishment().getId());
            professionals.save(p);
            r.addFlashAttribute("success", "Profissional salvo com sucesso.");
            return "redirect:/panel/professionals";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/professionals");
        }
    }

    @PostMapping("/panel/professionals/{id}")
    String updateProfessional(@PathVariable Long id, @Valid @ModelAttribute ProfessionalForm form, BindingResult binding, RedirectAttributes r) {
        if (binding.hasErrors()) {
            return panelError(r, bindingMessage(binding), "/panel/professionals");
        }
        try {
            Long est = current.establishmentId();
            Professional p = professionals.findByIdAndEstablishmentId(id, est)
                    .orElseThrow(() -> new IllegalArgumentException("Profissional não encontrado para este estabelecimento."));
            applyProfessionalFields(p, form.name(), form.bio(), form.whatsapp(), form.active(), form.serviceIds(), est);
            professionals.save(p);
            r.addFlashAttribute("success", "Profissional atualizado com sucesso.");
            return "redirect:/panel/professionals";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/professionals");
        }
    }

    @PostMapping("/panel/professionals/{id}/toggle")
    String toggleProf(@PathVariable Long id, RedirectAttributes r) {
        try {
            Professional p = professionals.findByIdAndEstablishmentId(id, current.establishmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Profissional não encontrado para este estabelecimento."));
            if (!p.isActive() && p.getServices().isEmpty()) {
                throw new IllegalArgumentException("Selecione pelo menos um serviço para este profissional.");
            }
            p.setActive(!p.isActive());
            professionals.save(p);
            r.addFlashAttribute("success", p.isActive() ? "Profissional reativado." : "Profissional arquivado.");
            return "redirect:/panel/professionals";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/professionals");
        }
    }

    @PostMapping("/panel/professionals/{id}/delete")
    String deleteProfessional(@PathVariable Long id, RedirectAttributes r) {
        try {
            Long est = current.establishmentId();
            Professional p = professionals.findByIdAndEstablishmentId(id, est)
                    .orElseThrow(() -> new IllegalArgumentException("Profissional não encontrado para este estabelecimento."));
            if (appointmentsCountByProfessional(est, id) > 0) {
                p.setActive(false);
                professionals.save(p);
                r.addFlashAttribute("error", "Este item já possui histórico e foi arquivado.");
            } else {
                professionals.delete(p);
                r.addFlashAttribute("success", "Profissional excluído com sucesso.");
            }
            return "redirect:/panel/professionals";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/professionals");
        }
    }

    @PostMapping("/panel/time-blocks")
    String block(@Valid @ModelAttribute TimeBlockForm form, BindingResult binding, RedirectAttributes r) {
        if (binding.hasErrors()) {
            return panelError(r, bindingMessage(binding), "/panel/time-blocks");
        }
        try {
            if (!form.endAt().isAfter(form.startAt())) {
                throw new IllegalArgumentException("Verifique os horários do bloqueio e tente novamente.");
            }
            AppUser u = current.user();
            TimeBlock b = new TimeBlock();
            b.setEstablishment(u.getEstablishment());
            b.setTitle(form.title().trim());
            b.setStartAt(form.startAt());
            b.setEndAt(form.endAt());
            b.setReason(form.reason());
            if (form.professionalId() != null) {
                b.setProfessional(professionals.findByIdAndEstablishmentId(form.professionalId(), u.getEstablishment().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Profissional não encontrado para este estabelecimento.")));
            }
            blocks.save(b);
            r.addFlashAttribute("success", "Bloqueio criado com sucesso.");
            return "redirect:/panel/time-blocks";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/time-blocks");
        }
    }

    @PostMapping("/panel/time-blocks/{id}/toggle")
    String toggleBlock(@PathVariable Long id, RedirectAttributes r) {
        try {
            TimeBlock b = blocks.findByIdAndEstablishmentId(id, current.establishmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Bloqueio não encontrado para este estabelecimento."));
            b.setActive(!b.isActive());
            blocks.save(b);
            r.addFlashAttribute("success", b.isActive() ? "Bloqueio reativado." : "Bloqueio pausado.");
            return "redirect:/panel/time-blocks";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/time-blocks");
        }
    }

    @PostMapping("/panel/settings")
    String settings(@Valid @ModelAttribute EstablishmentSettingsForm form, BindingResult binding, RedirectAttributes r) {
        if (binding.hasErrors()) {
            return panelError(r, bindingMessage(binding), "/panel/settings");
        }
        try {
            settingsService.update(current.user().getEstablishment(), form);
            r.addFlashAttribute("success", "Configurações salvas com sucesso.");
            return "redirect:/panel/settings";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/settings");
        }
    }

    @PostMapping("/panel/settings/business-hours")
    String businessHours(@ModelAttribute WeeklyBusinessHoursForm form, RedirectAttributes r) {
        try {
            AppUser user = current.user();
            List<BusinessHoursForm> days = form.getHours().stream()
                    .map(day -> new BusinessHoursForm(day.getDayOfWeek(), day.isOpen(), day.getOpeningTime(), day.getClosingTime()))
                    .toList();
            businessHours.update(user.getEstablishment(), days);
            r.addFlashAttribute("success", "Horários de funcionamento salvos com sucesso.");
            return "redirect:/panel/settings";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return panelError(r, ex.getMessage(), "/panel/settings");
        }
    }

    private AppUser shell(Model model, String activePage, String pageTitle, String pageSubtitle) {
        AppUser u = current.user();
        model.addAttribute("user", u);
        model.addAttribute("establishment", u.getEstablishment());
        model.addAttribute("activePage", activePage);
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("pageSubtitle", pageSubtitle);
        return u;
    }

    private DashboardService.Filter filter(LocalDate startDate, LocalDate endDate, Long professionalId, AppointmentStatus status) {
        LocalDate selectedStart = startDate == null ? LocalDate.now(clock) : startDate;
        return new DashboardService.Filter(selectedStart, endDate == null ? selectedStart : endDate, professionalId, status);
    }

    private Customer lookupCustomer(Long establishmentId, String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        try {
            String normalized = PhoneNormalizer.normalize(phone);
            return customers.findByEstablishmentIdAndPhoneNormalized(establishmentId, normalized).orElse(null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private List<Customer> customerList(Long establishmentId, String query) {
        if (query == null || query.isBlank()) {
            return customers.findByEstablishmentIdOrderByUpdatedAtDesc(establishmentId);
        }
        String text = query.trim().toLowerCase();
        String digits = query.replaceAll("\\D", "");
        if (digits.isBlank()) {
            digits = "__sem_telefone__";
        }
        return customers.searchByEstablishment(establishmentId, text, digits);
    }

    private List<ServiceItem> serviceList(Long establishmentId, String query) {
        List<ServiceItem> all = services.findByEstablishmentIdOrderByActiveDescSortOrderAscNameAsc(establishmentId);
        if (query == null || query.isBlank()) {
            return all;
        }
        String text = query.trim().toLowerCase();
        return all.stream()
                .filter(service -> service.getName().toLowerCase().contains(text)
                        || service.getDescription() != null && service.getDescription().toLowerCase().contains(text))
                .toList();
    }

    private List<Professional> professionalList(Long establishmentId, String query) {
        List<Professional> all = professionals.findByEstablishmentIdOrderByActiveDescSortOrderAscNameAsc(establishmentId);
        if (query == null || query.isBlank()) {
            return all;
        }
        String text = query.trim().toLowerCase();
        return all.stream()
                .filter(professional -> professional.getName().toLowerCase().contains(text)
                        || professional.getWhatsapp() != null && professional.getWhatsapp().toLowerCase().contains(text)
                        || professional.getServices().stream().anyMatch(service -> service.getName().toLowerCase().contains(text)))
                .toList();
    }

    private String panelError(RedirectAttributes r, String message, String path) {
        r.addFlashAttribute("error", message == null || message.isBlank() ? "Não foi possível concluir essa ação." : message);
        return "redirect:" + path;
    }

    private void applyServiceFields(ServiceItem service, ServiceForm form) {
        if (form.durationMinutes() % 15 != 0) {
            throw new IllegalArgumentException("Verifique os dados do serviço.");
        }
        service.setName(form.name().trim());
        service.setDurationMinutes(form.durationMinutes());
        service.setPrice(form.price());
        service.setDescription(form.description());
    }

    private void applyProfessionalFields(Professional professional, String name, String bio, String whatsapp, boolean active, List<Long> serviceIds, Long establishmentId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Informe o nome do profissional.");
        }
        Set<ServiceItem> selectedServices = selectedServices(establishmentId, serviceIds);
        if (active && selectedServices.isEmpty()) {
            throw new IllegalArgumentException("Selecione pelo menos um serviço para este profissional.");
        }
        professional.setName(name.trim());
        professional.setBio(bio);
        professional.setWhatsapp(whatsapp);
        professional.setActive(active);
        professional.setServices(selectedServices);
    }

    private Set<ServiceItem> selectedServices(Long establishmentId, List<Long> serviceIds) {
        Set<ServiceItem> selected = new LinkedHashSet<>();
        if (serviceIds == null) {
            return selected;
        }
        for (Long serviceId : serviceIds) {
            ServiceItem service = services.findByIdAndEstablishmentId(serviceId, establishmentId)
                    .filter(ServiceItem::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("Selecione apenas serviços ativos deste estabelecimento."));
            selected.add(service);
        }
        return selected;
    }

    private long appointmentsCountByService(Long establishmentId, Long serviceId) {
        return appointments.countAppointmentsForService(establishmentId, serviceId);
    }

    private long appointmentsCountByProfessional(Long establishmentId, Long professionalId) {
        return appointments.countAppointmentsForProfessional(establishmentId, professionalId);
    }

    private String bindingMessage(BindingResult binding) {
        return binding.getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Verifique os dados e tente novamente." : error.getDefaultMessage())
                .orElse("Verifique os dados e tente novamente.");
    }
}
