package br.com.agendafacilpro.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import br.com.agendafacilpro.domain.Establishment;
import br.com.agendafacilpro.domain.EstablishmentSettings;
import br.com.agendafacilpro.domain.Professional;
import br.com.agendafacilpro.domain.ServiceItem;
import br.com.agendafacilpro.service.AppointmentService;
import br.com.agendafacilpro.service.CatalogService;
import br.com.agendafacilpro.service.DashboardService;
import br.com.agendafacilpro.service.EstablishmentSettingsService;
import br.com.agendafacilpro.ui.AppointmentViewUtil;

class PresentationTemplatesTest {
    private static final LocalDate DATE = LocalDate.of(2030, 1, 7);
    private static final Pattern POST_FORM = Pattern.compile("<form\\b[^>]*method=\"post\"[^>]*>(.*?)</form>", Pattern.DOTALL);
    private final ExtendedModelMap model = new ExtendedModelMap();
    private final MockServletContext servletContext = new MockServletContext();
    private StaticWebApplicationContext context;
    private ThymeleafViewResolver views;

    @BeforeEach
    void setUp() {
        context = new StaticWebApplicationContext();
        context.setServletContext(servletContext);
        context.getBeanFactory().registerSingleton("view", new AppointmentViewUtil());
        context.refresh();
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        var templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setTemplateMode("HTML");
        templates.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);
        views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        views.setApplicationContext(context);

        var establishment = new Establishment();
        establishment.setId(1L);
        establishment.setName("Estúdio Horizonte");
        establishment.setSlug("agenda-demo");
        establishment.setDescription("Atendimento com horário marcado para cuidar do seu dia.");
        establishment.setCity("São Paulo");
        var service = new ServiceItem();
        service.setId(2L);
        service.setName("Atendimento completo");
        service.setDurationMinutes(60);
        service.setActive(true);
        var professional = new Professional();
        professional.setId(3L);
        professional.setName("Profissional de demonstração");
        professional.setActive(true);
        professional.getServices().add(service);
        var settings = EstablishmentSettings.defaultsFor(establishment);
        var catalog = mock(CatalogService.class);
        var settingsService = mock(EstablishmentSettingsService.class);
        when(catalog.establishment("agenda-demo")).thenReturn(establishment);
        when(catalog.services(1L)).thenReturn(List.of(service));
        when(settingsService.forEstablishment(establishment)).thenReturn(settings);
        var controller = new PublicBookingController(catalog, mock(AppointmentService.class), settingsService,
                Clock.fixed(Instant.parse("2030-01-07T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));
        controller.establishment("agenda-demo", model);

        model.addAttribute("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "presentation-test-token"));
        model.addAttribute("service", service);
        model.addAttribute("professional", professional);
        model.addAttribute("professionals", List.of(professional));
        model.addAttribute("activeProfessionals", List.of(professional));
        model.addAttribute("activeServices", List.of(service));
        model.addAttribute("selectedDate", DATE);
        model.addAttribute("selectedTime", LocalTime.of(9, 0));
        model.addAttribute("todayDate", DATE);
        model.addAttribute("next7Date", DATE.plusDays(6));
        model.addAttribute("customerName", "Cliente de demonstração");
        model.addAttribute("customerPhone", "11999999999");
        model.addAttribute("slots", List.of(new AppointmentService.Slot(LocalTime.of(9, 0), LocalTime.of(10, 0),
                true, AppointmentService.SlotReason.AVAILABLE, "Disponível", null)));
        model.addAttribute("closedDay", false);
        model.addAttribute("slotSuggestions", List.of());
        model.addAttribute("summary", new AppointmentService.Summary("Cliente", establishment.getName(),
                "agenda-demo", service.getName(), professional.getName(), DATE.atTime(9, 0), DATE.atTime(10, 0),
                "Confirmado", "Seu agendamento está confirmado", "Esperamos você no horário marcado.", null));
        model.addAttribute("activePage", "dashboard");
        model.addAttribute("pageTitle", "Painel");
        model.addAttribute("pageSubtitle", "Acompanhe seus atendimentos.");
        model.addAttribute("dashboard", new DashboardService.Data(
                List.of(new DashboardService.Metric("Agendamentos hoje", 0, "Atendimentos do dia")),
                List.of(), List.of(), List.of(), List.of(), List.of()));
        model.addAttribute("reports", new DashboardService.Reports(List.of(), List.of()));
        model.addAttribute("filter", new DashboardService.Filter(DATE, DATE.plusDays(6), null, null));
        model.addAttribute("statuses", br.com.agendafacilpro.domain.AppointmentStatus.values());
        model.addAttribute("customers", List.of());
        model.addAttribute("timeBlocks", List.of());
        model.addAttribute("businessHours", List.of());
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"login", "public/establishment", "public/professionals", "public/slots",
            "public/data", "public/confirm", "public/success", "panel/dashboard", "panel/agenda",
            "panel/appointments", "panel/customers", "panel/services", "panel/professionals",
            "panel/time-blocks", "panel/settings", "panel/reports", "error", "error/403", "error/404", "error/500"})
    void templatesRenderAndPreservePostCsrf(String template) throws Exception {
        int step = switch (template) {
            case "public/professionals" -> 2;
            case "public/slots" -> 4;
            case "public/data" -> 5;
            case "public/confirm", "public/success" -> 6;
            default -> 1;
        };
        model.addAttribute("currentStep", step);
        model.addAttribute("flowProgress", step * 100 / 6);
        String html = render(template);
        assertThat(html).contains("/css/app.css").doesNotContainPattern("\\sth:[\\w-]+=\"");
        var forms = POST_FORM.matcher(html);
        while (forms.find()) {
            assertThat(forms.group(1)).as("CSRF em %s", template)
                    .contains("name=\"_csrf\"", "value=\"presentation-test-token\"");
        }
        if (template.startsWith("public/")) {
            String stepper = html.substring(html.indexOf("<ol>"), html.indexOf("</ol>"));
            assertThat(Pattern.compile("<li\\b").matcher(stepper).results().count()).isEqualTo(6);
            assertThat(stepper).contains("aria-current=\"step\"");
            assertThat(html).contains("Etapa " + step + " de 6");
            assertThat(Pattern.compile("class=\"\\s*done\\s*\"").matcher(stepper).results().count()).isEqualTo(step - 1);
        }
        if (template.startsWith("panel/")) {
            for (String route : List.of("/panel", "/panel/agenda", "/panel/appointments", "/panel/customers",
                    "/panel/services", "/panel/professionals", "/panel/time-blocks", "/panel/settings", "/panel/reports")) {
                assertThat(html).contains("href=\"" + route + "\"");
            }
            assertThat(html).contains("aria-current=\"page\"");
        }
    }

    @Test
    void loginKeepsAuthenticationContractAndNeutralPlaceholder() throws Exception {
        assertThat(render("login")).contains("action=\"/login\"", "method=\"post\"", "name=\"email\"",
                "name=\"password\"", "placeholder=\"voce@empresa.com\"").doesNotContain("admin@demo.local");
    }

    @Test
    void messagesRemainEscapedAndTemplatesNeverUseUnescapedHtml() throws Exception {
        model.addAttribute("error", "<script>alert('unsafe')</script>");
        assertThat(render("public/data")).contains("&lt;script&gt;").doesNotContain("<script>alert");
        try (var files = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".html")).toList()) {
                assertThat(Files.readString(file)).as(file.toString()).doesNotContain("th:utext");
            }
        }
    }

    private String render(String template) throws Exception {
        var request = new MockHttpServletRequest(servletContext);
        var response = new MockHttpServletResponse();
        request.setAttribute("_csrf", model.get("_csrf"));
        var view = views.resolveViewName(template, Locale.forLanguageTag("pt-BR"));
        assertThat(view).isNotNull();
        view.render(model, request, response);
        return response.getContentAsString();
    }
}
