# Arquitetura

## Objetivo

O AgendaFacil Pro é um sistema de agendamento online para estabelecimentos. Ele oferece uma página pública para o cliente final escolher serviço, profissional, data e horário, e um painel autenticado para o estabelecimento acompanhar reservas, pendências, histórico, serviços, profissionais e bloqueios de agenda.

O cliente final não cria conta. O acesso administrativo é feito por usuários do estabelecimento.

## Stack

- Java 17
- Spring Boot
- Spring MVC
- Thymeleaf
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- Maven
- JUnit 5 / AssertJ / Mockito

## Camadas

### Controller

Recebe requisições HTTP, resolve parâmetros de rota/formulário, monta o `Model` para Thymeleaf e delega regras para services.

- `PublicBookingController`: fluxo público de agendamento em `/agenda/{slug}`.
- `PanelController`: painel autenticado em `/panel`.
- `AuthController`: tela de login.
- `GlobalExceptionHandler`: tratamento de erros para mensagens amigáveis.

### Service

Concentra regras de negócio, transações e coordenação entre repositórios.

- `AppointmentService`: disponibilidade, criação de reserva, aprovação, recusa, cancelamento, conclusão, falta e expiração.
- `AppointmentAuditService`: auditoria das decisões administrativas sobre agendamentos.
- `BookingGuardService`: barreiras antifraude por telefone, IP e honeypot.
- `LoginAttemptService`: rate limit e bloqueio temporario de tentativas do login administrativo.
- `CatalogService`: catálogo público de estabelecimento, serviços e profissionais ativos.
- `DashboardService`: dados agregados do painel.
- `CurrentUserService`: estabelecimento do usuário autenticado.

### Repository

Abstrai acesso ao banco com Spring Data JPA. Consultas sempre que necessário incluem `establishmentId` para evitar vazamento entre estabelecimentos.

### DTO / Records

O projeto usa records simples dentro dos services e forms de controller para transportar dados calculados e validar entradas:

- `AppointmentService.Slot`
- `AppointmentService.Summary`
- `DashboardService.Metric`
- `DashboardService.Day`
- `DashboardService.Data`
- forms publicos e administrativos com Bean Validation antes de chamar services.

### Entity

Representa tabelas persistidas:

- `Establishment`
- `AppUser`
- `Customer`
- `ServiceItem`
- `Professional`
- `Appointment`
- `BookingAttempt`
- `TimeBlock`
- `AppointmentAudit`

### Util / Regras de Domínio

- `AppointmentViewUtil`: labels, classes visuais e permissões de ação exibidas na view.
- `PhoneNormalizer`: normalização e validação de telefone.
- `AppointmentRules`: status que bloqueiam agenda e regra canônica de conflito de horários.

## Fluxo de agendamento manual

1. Dono acessa o painel autenticado.
2. Pode consultar um cliente por telefone.
3. O telefone é normalizado antes da busca.
4. Se o cliente existir no estabelecimento, o painel exibe nome, faltas e bloqueio.
5. Se não existir, o dono cria o cliente rápido com nome e telefone.
6. Dono escolhe serviço ativo, profissional ativo, data e horário.
7. `AppointmentService.createManual` valida serviço, profissional, horário passado, bloqueios e conflitos.
8. O cliente é reutilizado por `establishmentId + phoneNormalized`, sem duplicar telefone no mesmo estabelecimento.
9. O agendamento é salvo como `CONFIRMED`.
10. A criação manual registra auditoria.
11. Por ficar `CONFIRMED`, o horário passa a bloquear a página pública.

## Fluxo de agendamento

1. Cliente acessa `/agenda/{slug}`.
2. O sistema localiza um estabelecimento ativo pelo slug.
3. Cliente escolhe serviço ativo.
4. Cliente escolhe profissional ativo.
5. `AppointmentService.slots` calcula horários de 30 em 30 minutos entre 08:00 e 18:00, usando a duração do serviço.
6. Cada horário é recusado se já passou, se houver `TimeBlock` ativo ou se houver agendamento conflitante com status bloqueante.
7. Cliente informa nome e telefone; o campo honeypot deve permanecer vazio.
8. Antes de salvar, `/agenda/{slug}/revisar` revalida o slot e mostra uma tela de confirmação com todos os dados escolhidos.
9. Somente o POST final em `/agenda/{slug}/confirmar` cria o agendamento.
10. `BookingGuardService` normaliza telefone, valida limites antifraude por telefone/IP e registra a tentativa.
11. `AppointmentService.create` revalida serviço, profissional, horário passado, bloqueio e conflito.
12. Cliente é localizado por `establishmentId + phoneNormalized`, ou criado se ainda não existir naquele estabelecimento.
13. Cliente bloqueado não agenda.
14. Cliente novo ou com 2 ou mais faltas gera `PENDING_APPROVAL`; demais casos geram `CONFIRMED`.
15. O cliente vê a tela de sucesso por `publicToken`, com status em português e orientação sobre o próximo passo.

## Separação por estabelecimento

O isolamento é feito por `establishment_id` nas entidades principais. O painel sempre usa o estabelecimento do usuário autenticado via `CurrentUserService`. Repositórios e services administrativos buscam registros por `id + establishmentId`, por exemplo `findByIdAndEstablishmentId`.

Na página pública, o slug resolve um estabelecimento ativo e todas as consultas seguintes usam o `id` desse estabelecimento.

## Painel e página pública

A página pública cria solicitações de agendamento e não exige login do cliente final. O painel é autenticado com Spring Security e permite ao estabelecimento gerenciar o resultado dessas solicitações:

- aprovar pendências;
- recusar pendências;
- cancelar confirmados ou pendentes;
- concluir atendimentos;
- registrar faltas;
- configurar serviços, profissionais e bloqueios.

Ambos os lados usam as mesmas regras centrais de disponibilidade e conflito em `AppointmentService`.

O expediente semanal possui um intervalo por dia em `establishment_business_hours`. Dias fechados não geram slots. `BusinessHoursService` centraliza a grade de 30 minutos, ancorada na abertura, e valida se a duração cabe antes do fechamento. Pausas e exceções continuam em `TimeBlock`. O timezone operacional do MVP vem de `APP_TIME_ZONE`, com padrão `America/Sao_Paulo`; as decisões da agenda usam um `Clock` explícito, a JVM é alinhada ao mesmo fuso para manter chamadas legadas coerentes e cada conexão PostgreSQL recebe esse timezone de sessão.

## Catálogo profissional

Profissionais e serviços se relacionam por `professional_services`. O lado público consulta apenas profissionais ativos, do mesmo estabelecimento e vinculados ao serviço escolhido. O painel permite editar os vínculos de cada profissional.

O banco possui proteção contra vínculo cruzado entre estabelecimentos, e o backend repete essa validação antes de criar agendamentos públicos ou manuais.

## Disponibilidade real

`AppointmentService` calcula slots com a duração real do serviço. O POST de confirmação também revalida a grade, expediente, bloqueios, conflitos e vínculo profissional-serviço. Assim, manipular URL ou formulário não permite reservar horário fora da grade ou com profissional não qualificado.
