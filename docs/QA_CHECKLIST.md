# Checklist de QA

## Agenda pública

- [ ] Agendar em horário disponível cria reserva.
- [ ] Fluxo público mostra stepper nas etapas principais.
- [ ] Etapas após serviço mostram resumo compacto do que já foi escolhido.
- [ ] A etapa de dados leva para revisão e não salva agendamento ainda.
- [ ] A etapa de confirmação salva somente ao clicar em "Confirmar agendamento".
- [ ] Tela de sucesso explica claramente se ficou confirmado ou pendente.
- [ ] Tentar agendar horário ocupado exibe mensagem amigável e não cria duplicidade.
- [ ] Reserva pendente bloqueia o horário enquanto válida.
- [ ] Reserva pendente expirada libera o horário.
- [ ] Cliente bloqueado não consegue agendar.
- [ ] Cliente novo entra como pendente.
- [ ] Cliente com 2 faltas entra como pendente.
- [ ] Página pública funciona em celular.
- [ ] Mensagens e badges exibem labels em português, sem enum cru.

## Painel

- [ ] Dono consegue criar novo agendamento manual.
- [ ] Agendamento manual em horário ocupado falha.
- [ ] Agendamento manual reutiliza cliente por telefone normalizado.
- [ ] Agendamento manual fica confirmado.
- [ ] Horário manual fica indisponível na página pública.
- [ ] Cliente com faltas exibe aviso humano.
- [ ] Cliente bloqueado exige confirmação explícita do dono.
- [ ] Cancelamento libera horário.
- [ ] Recusa de pendência libera horário.
- [ ] Aprovação revalida conflito antes de confirmar.
- [ ] Marcar falta incrementa contador do cliente.
- [ ] Concluir atendimento remove bloqueio futuro do horário sem apagar histórico.
- [ ] Estabelecimento A não acessa nem altera dados do estabelecimento B.

## Segurança

- [ ] POST autenticado no painel exige CSRF.
- [ ] POST público de confirmação exige CSRF.
- [ ] Rotas `/panel/**` exigem login.
- [ ] Rotas públicas de `/agenda/**` continuam acessíveis sem login.
- [ ] Logs de antifraude não expõem telefone completo, nome ou senha.

## Banco e configuração

- [ ] Flyway executa sem alterar migrations antigas.
- [ ] Aplicação sobe com credenciais via variáveis de ambiente.
- [ ] Nenhuma senha real é adicionada ao repositório.
- [ ] Fora da combinacao exclusiva `dev,demo`, o tenant e a credencial historica de demonstracao ficam desativados.
- [ ] Com `dev,demo`, os dados de demonstracao funcionam somente para desenvolvimento local.
- [ ] Com `staging,demo`, `prod,demo` ou `dev,demo,prod`, o ativador de demonstracao nao e carregado.

## Testes automatizados mínimos

- [ ] `mvn clean test` passa em Java 17.
- [ ] PhoneNormalizer cobre máscara, sem máscara e inválido.
- [ ] Status bloqueantes e liberadores cobertos.
- [ ] Regra de conflito cobre igualdade, sobreposição e fronteiras sem conflito.
- [ ] Regra de faltas cobre incremento e exigência de aprovação manual.
## Configuracoes por estabelecimento

- [ ] Tela de configuracoes carrega no painel.
- [ ] Salvar configuracoes usa POST com CSRF.
- [ ] Configuracoes salvam para o estabelecimento autenticado.
- [ ] Cliente novo fica pendente quando a opcao estiver ligada.
- [ ] Cliente novo confirma automaticamente quando a opcao permitir.
- [ ] Limite de agendamentos futuros por telefone bloqueia novo horario.
- [ ] Limite de tentativas por telefone bloqueia excesso em 1 hora.
- [ ] Limite de tentativas por IP bloqueia excesso em 1 hora.
- [ ] Cliente com faltas entra como pendente conforme configuracao.
- [ ] Cliente com faltas acima do bloqueio nao agenda online.
- [ ] Servico longo entra como pendente conforme configuracao.
- [ ] Pendencia expirada por tempo libera horario.
- [ ] Precos aparecem ou somem na pagina publica conforme configuracao.

## Catálogo profissional

- [ ] Profissional vinculado ao serviço aparece na etapa pública.
- [ ] Profissional não vinculado ao serviço não aparece na etapa pública.
- [ ] Acessar horários por URL com profissional não qualificado é bloqueado.
- [ ] Enviar POST público com profissional não qualificado é bloqueado no backend.
- [ ] Enviar POST manual com profissional não qualificado é bloqueado no backend.

## Duração real e slots

- [ ] Com atendimento existente 11:00-12:00, serviço de 30 min às 10:30 fica disponível.
- [ ] Com atendimento existente 11:00-12:00, serviço de 60 min às 10:30 fica indisponível.
- [ ] Com atendimento existente 11:00-12:00, serviço de 30 min às 12:00 fica disponível.
- [ ] POST com horário fora da grade de 30 minutos é rejeitado.
- [ ] POST com horário fora do expediente é rejeitado.
- [ ] Página pública mostra motivos simples de indisponibilidade, sem enum cru.
- [ ] Slot em conflito ou que não cabe mostra sugestão simples quando houver alternativa.

## Edição, exclusão e arquivamento

- [ ] Dono edita nome, descrição, duração, preço e status de serviço.
- [ ] Dono não consegue salvar serviço com duração inválida ou preço negativo.
- [ ] Dono edita nome, WhatsApp, bio, status e serviços do profissional.
- [ ] Profissional ativo exige pelo menos um serviço selecionado.
- [ ] Serviço sem histórico pode ser excluído.
- [ ] Serviço com histórico é arquivado e mostra mensagem humana.
- [ ] Profissional sem histórico pode ser excluído.
- [ ] Profissional com histórico é arquivado e mostra mensagem humana.
- [ ] Item inativo não aparece no agendamento público.

## Hardening HTTP e login

- [ ] Respostas principais enviam Content-Security-Policy.
- [ ] Respostas principais enviam X-Frame-Options DENY.
- [ ] Respostas principais enviam X-Content-Type-Options nosniff.
- [ ] Respostas principais enviam Referrer-Policy strict-origin-when-cross-origin.
- [ ] Em producao, cookie `AGENDAFACIL_SESSION` fica `httpOnly`, `secure` e `sameSite=strict`.
- [ ] Em dev, cookie secure fica desativado apenas para rodar sem HTTPS local.
- [ ] Login administrativo bloqueia forca bruta por e-mail e IP.
- [ ] Mensagem de login nao revela se o usuario existe.
- [ ] Validacao rejeita payload invalido em formularios publicos e administrativos.
- [ ] Estabelecimento B nao acessa nem altera dados de A usando IDs reais.
- [ ] Paginas 403, 404 e 500 nao mostram stacktrace, classe de excecao ou caminho interno.
