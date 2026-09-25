# Regras de Negócio

## Cliente final

- Cliente final não cria conta.
- Cliente final não possui senha.
- Cliente é identificado por telefone normalizado + estabelecimento.
- Telefone não é chave primária.
- O banco usa `customers.id` como chave primária e uma restrição única em `establishment_id + phone_normalized`.
- O mesmo telefone pode existir em estabelecimentos diferentes como clientes separados.

## Telefone

- Máscaras são removidas antes de salvar.
- O DDI `55` é removido quando o número está no padrão DDI + DDD + celular.
- Telefones normalizados válidos têm 10 ou 11 dígitos.

## Status de agendamento

| Status | Label em português | Bloqueia horário? |
| --- | --- | --- |
| `PENDING_APPROVAL` | Pendente | Sim, enquanto não expirado |
| `CONFIRMED` | Confirmado | Sim |
| `COMPLETED` | Concluído | Não |
| `NO_SHOW` | Faltou | Não |
| `CANCELLED` | Cancelado | Não |
| `EXPIRED` | Expirado | Não |

## Status que bloqueiam horário

- `CONFIRMED`
- `PENDING_APPROVAL`

Uma pendência deixa de bloquear após expirar. O sistema expira pendências cujo `startAt` já passou.

## Status que liberam horário

- `CANCELLED`
- `NO_SHOW`
- `COMPLETED`
- `EXPIRED`

Recusar uma pendência altera o status para `CANCELLED` e libera o horário. Cancelar um agendamento confirmado ou pendente também libera o horário.

## Regra de conflito

Dois intervalos conflitam quando:

```text
novoInicio < agendamentoExistenteFim
E
novoFim > agendamentoExistenteInicio
```

Consequências:

- horário exatamente igual conflita;
- sobreposição parcial conflita;
- um horário que termina exatamente quando o próximo começa não conflita;
- um horário que começa exatamente quando o anterior termina não conflita.

Essa regra e reforcada no PostgreSQL pela exclusion constraint
`ex_appointments_no_blocking_overlap`, usando intervalos `[inicio, fim)` para
status `CONFIRMED` e `PENDING_APPROVAL`. A consulta previa do backend melhora a
experiencia, mas a constraint e a garantia final contra requisicoes concorrentes.

## Faltas

- Marcar falta muda o agendamento de `CONFIRMED` para `NO_SHOW`.
- Marcar falta incrementa `Customer.noShowCount`.
- Cliente com 2 ou mais faltas pode solicitar agenda online, mas a nova reserva fica `PENDING_APPROVAL`.
- A base atual não bloqueia automaticamente ao chegar em 3 faltas; o bloqueio depende do campo `Customer.blocked`.

## Cliente bloqueado

- Cliente com `blocked = true` não consegue criar agendamento online.
- A validação ocorre após localizar o cliente por `establishmentId + phoneNormalized`.
- A mensagem orienta o cliente a falar com o estabelecimento pelo WhatsApp.

## Aprovação manual

Reservas entram como `PENDING_APPROVAL` quando:

- o cliente é novo naquele estabelecimento; ou
- o cliente já tem 2 ou mais faltas.

Reservas pendentes bloqueiam horário enquanto válidas. O estabelecimento pode aprovar, recusar ou cancelar.

## Liberação de horário

- `CONFIRMED` bloqueia horário.
- `PENDING_APPROVAL` bloqueia horário enquanto válido.
- Recusar libera horário.
- Cancelar libera horário.
- Expirar pendência libera horário.
- Concluir atendimento libera horário para regras futuras, mantendo histórico.
- Marcar falta libera horário para regras futuras, mantendo histórico.

## Agendamento manual pelo dono

- Agendamento manual só existe no painel autenticado.
- O dono informa nome, telefone, serviço, profissional, data, horário e observação interna opcional.
- O sistema normaliza o telefone e reutiliza cliente existente por `establishmentId + phoneNormalized`.
- Se não houver cliente, cria um cliente rápido.
- Agendamento manual sempre entra como `CONFIRMED`.
- Agendamento manual usa a mesma regra de conflito do agendamento público.
- Cliente bloqueado exige confirmação explícita do dono para criar manualmente.
- Cliente com 2 ou mais faltas deve ser apresentado com aviso humano ao dono.
- Criação manual não deve duplicar cliente com mesmo telefone no mesmo estabelecimento.
- Observação interna não aparece para o cliente final.

## Auditoria

O sistema registra auditoria para:

- criação manual;
- aprovação;
- recusa;
- cancelamento;
- falta;
- conclusão.
## Configuracoes por estabelecimento

Cada estabelecimento possui regras proprias em `establishment_settings`.

| Configuracao | Efeito |
| --- | --- |
| `newClientRequiresApproval` | Cliente novo entra como `PENDING_APPROVAL` quando ligado. |
| `pendingExpirationMinutes` | Tempo maximo, em minutos, para uma pendencia segurar horario. |
| `maxFutureAppointmentsPerPhone` | Limite de reservas futuras bloqueantes para o mesmo telefone no estabelecimento. |
| `maxAttemptsPerPhoneHour` | Limite de tentativas por telefone em 1 hora. |
| `maxAttemptsPerIpHour` | Limite de tentativas por IP em 1 hora. |
| `noShowCountForManualApproval` | A partir dessa quantidade de faltas, nova reserva publica fica `PENDING_APPROVAL`. |
| `noShowCountForBlock` | A partir dessa quantidade de faltas, o cliente nao agenda online. |
| `minHoursBeforeClientCancel` | Preparacao para cancelamento pelo cliente. |
| `longServiceManualApprovalMinutes` | Servicos acima dessa duracao ficam `PENDING_APPROVAL`. |
| `showPricesOnPublicPage` | Mostra ou esconde precos na pagina publica. |

As configuracoes nunca mudam a identidade do cliente: telefone normalizado + estabelecimento. Tambem nao mudam a regra de bloqueio de horario: apenas `CONFIRMED` e `PENDING_APPROVAL` ainda valido bloqueiam.

## Profissional x serviço

- Um profissional só aparece na etapa pública de escolha quando está ativo, pertence ao mesmo estabelecimento e está vinculado ao serviço escolhido.
- O backend revalida o vínculo no agendamento público e no agendamento manual.
- Manipular URL ou formulário com profissional que não realiza o serviço retorna mensagem humana: "Esse profissional não realiza o serviço escolhido."
- A tabela `professional_services` impede vínculos duplicados e possui validação no banco para evitar vínculo entre estabelecimentos diferentes.

## Slot válido no POST

- A tela de horários é apenas uma conveniência; o POST nunca é confiado sozinho.
- O horário enviado precisa estar na grade de 30 minutos, dentro do expediente configurado no MVP, entre 08:00 e 18:00.
- O fim calculado usa `inicio + durationMinutes` do serviço.
- O slot é recusado se estiver no passado, fora da grade, bloqueado por `TimeBlock`, em conflito com agendamento bloqueante ou se o serviço não couber antes do fim do expediente.
- Mensagem padrão para horário manipulado ou indisponível: "Esse horário não está disponível para este serviço."

## Horário semanal

- Cada estabelecimento possui configuração independente para os sete dias.
- Um dia aberto possui um único intervalo, com abertura anterior ao fechamento.
- Um dia fechado não possui horários e não gera slots reserváveis.
- A grade permanece em 30 minutos e começa no horário de abertura do dia.
- O serviço pode terminar exatamente no fechamento, mas nunca ultrapassá-lo.
- Configuração ausente falha de forma fechada e não usa expediente padrão em runtime.
- Almoço, pausas, feriados, férias e outras exceções continuam em `TimeBlock`.
- O timezone explícito do MVP é `America/Sao_Paulo`.

## Confirmação antes de salvar

- A etapa de dados não salva o agendamento.
- O POST `/agenda/{slug}/revisar` revalida o slot e exibe uma tela de revisão com serviço, duração, preço quando habilitado, profissional, data, horário, nome e WhatsApp.
- O agendamento só é persistido no POST final `/agenda/{slug}/confirmar`.
- Se a validação final falhar por concorrência ou dado inválido, o sistema volta para a etapa de dados com mensagem humana sempre que possível.

## Conflito por duração real

- O sistema sempre compara intervalos completos, não apenas a hora inicial.
- Exemplo com atendimento existente das 11:00 às 12:00:
  - serviço de 30 minutos às 10:30 é permitido, pois termina às 11:00;
  - serviço de 60 minutos às 10:30 é bloqueado, pois terminaria às 11:30;
  - serviço de 30 minutos às 12:00 é permitido;
  - serviço das 11:30 às 12:30 é bloqueado.

## Motivos e sugestões de indisponibilidade

- Slots públicos usam motivos humanos: "Disponível", "Horário já passou", "Indisponível no momento", "Esse serviço não cabe nesse horário", "Horário bloqueado pelo estabelecimento" e "Fora do horário de atendimento".
- A tela pública não deve exibir enum, payload técnico, entidade ou stacktrace.
- Quando houver conflito ou quando o serviço não couber, o sistema sugere até 3 próximos horários com o mesmo profissional e até 2 outros profissionais qualificados no mesmo horário.

## Exclusão x arquivamento

- Serviço sem histórico de agendamentos pode ser excluído permanentemente.
- Serviço com histórico não é excluído; é arquivado/inativado para preservar histórico.
- Profissional sem histórico de agendamentos pode ser excluído permanentemente.
- Profissional com histórico não é excluído; é arquivado/inativado para preservar histórico.
- Itens inativos não aparecem no agendamento público nem em novos agendamentos, mas continuam visíveis no histórico antigo.
