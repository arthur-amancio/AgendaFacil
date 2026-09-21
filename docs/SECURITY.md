# Segurança

## Spring Security

O projeto usa Spring Security com login por formulário para o painel administrativo. Rotas públicas são limitadas a login, assets, erro e fluxo público de agenda em `/agenda/**`. Rotas `/panel/**` exigem autenticação.

Não remover Spring Security nem alterar a regra de autenticação sem revisão explícita.

## CSRF

CSRF permanece ativo pela configuração padrão do Spring Security. Formulários POST devem enviar o token CSRF gerado pelo Thymeleaf/Spring Security.

Não desabilitar CSRF para resolver erro de formulário; corrigir o formulário ou teste.

## BCrypt

Senhas administrativas são armazenadas como hash BCrypt. O `PasswordEncoder` configurado é `BCryptPasswordEncoder`.

## Senhas e variáveis de ambiente

Não salvar senha real no código, em documentação ou em migrations novas. Use variáveis de ambiente para configuração sensível:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

As migrations historicas V1 e V5 contem dados locais de demonstracao e nao devem
ser reescritas, pois podem ter sido aplicadas em bancos existentes. A V7 desativa
o estabelecimento `agenda-demo`, todos os seus usuarios e a credencial conhecida.
Ela tambem substitui o hash historico por um valor que nao e aceito pelo encoder.

Para desenvolvimento local, os dados so sao reativados quando os profiles `dev`
e `demo` estao explicitamente ativos e `prod` esta ausente. Qualquer outra
combinacao repete a neutralizacao de forma idempotente a cada inicializacao;
assim, um banco usado anteriormente como demo nao mantem a credencial ativa ao
iniciar em staging, QA ou producao. Nunca use `demo` em uma instalacao publicada.

Antes de atualizar um banco ainda anterior a V5, consulte `flyway_schema_history`
e verifique os registros com IDs fixos usados por ela: estabelecimento 1,
servicos 1 a 4 e profissionais 1 a 3. A V5 historica usa atualizacoes por ID e
`ON CONFLICT (id) DO UPDATE`; se algum desses IDs ja contiver dado real,
interrompa o deploy, faca backup e resolva a colisao antes de executar as
migrations.

## Proteção contra acesso cruzado

O isolamento principal é por `establishmentId`.

- O painel obtém o estabelecimento pelo usuário autenticado.
- Operações administrativas buscam registros por `id + establishmentId`.
- Serviços, profissionais, bloqueios e agendamentos do painel não devem ser carregados apenas por `id`.
- A página pública resolve o estabelecimento pelo slug e usa esse `id` nas consultas seguintes.

Essa regra impede que o estabelecimento A aprove, cancele, liste ou altere dados do estabelecimento B.

## Controle por establishmentId

Toda regra que lê ou altera dados de negócio deve receber ou derivar o `establishmentId` confiável:

- no painel, do usuário autenticado;
- na agenda pública, do slug ativo;
- nunca de um campo livre enviado pelo cliente final.

## Ações administrativas

Ações críticas do painel continuam por POST e protegidas por CSRF. A criação manual, aprovação, recusa, cancelamento, falta e conclusão usam o estabelecimento do usuário autenticado e registram auditoria.

## Antifraude por telefone/IP

`BookingGuardService` registra tentativas de reserva por estabelecimento, telefone normalizado e IP. A regra atual bloqueia temporariamente:

- 3 ou mais tentativas para o mesmo telefone em 30 minutos;
- 10 ou mais tentativas para o mesmo IP em 30 minutos.

## Honeypot

O formulário público possui um campo honeypot. Se ele vier preenchido, a tentativa é tratada como suspeita e a reserva não é criada.

## Logs

Logs de suspeita registram estabelecimento e motivo, sem gravar nome, telefone completo ou outros dados sensíveis. Manter essa política em novas regras.

## Cliente final sem senha

O cliente final não precisa de senha porque não acessa um painel com dados privados. Ele apenas solicita agendamento por link público. A identificação operacional é feita por telefone normalizado + estabelecimento, e ações sensíveis continuam no painel autenticado do estabelecimento.
## Configuracoes e antifraude

As configuracoes do estabelecimento sao salvas por POST em `/panel/settings`, com CSRF ativo. O `establishmentId` vem do usuario autenticado, nao do formulario.

`BookingGuardService` usa limites por estabelecimento para tentativas por telefone e por IP em 1 hora. Isso evita que excesso de tentativas em um estabelecimento afete outro.

O agendamento publico tambem valida limite de horarios futuros por telefone, bloqueio por faltas e servicos longos. Mensagens para cliente final continuam simples e nao expoem detalhes internos de antifraude.

## Validação de catálogo e slot

O serviço, o profissional e o vínculo entre eles são sempre buscados pelo `establishmentId` confiável. O cliente pode enviar IDs no formulário público, mas o backend rejeita profissional sem vínculo com o serviço escolhido.

O horário enviado no POST público ou manual também é revalidado no backend. A aplicação não confia apenas nos links renderizados pelo HTML para decidir disponibilidade.

## Headers HTTP

O `SecurityConfig` envia headers para reduzir risco de clickjacking, sniffing e vazamento de origem:

- `Content-Security-Policy` com `default-src 'self'`, `frame-ancestors 'none'` e `object-src 'none'`.
- `Strict-Transport-Security` para producao em HTTPS.
- `X-Content-Type-Options: nosniff`.
- `X-Frame-Options: DENY`.
- `Referrer-Policy: strict-origin-when-cross-origin`.

A CSP permite `style-src 'self' 'unsafe-inline'` porque o fluxo Thymeleaf atual usa estilos inline em alguns pontos. Scripts continuam limitados a `'self'`.

## Cookies de sessao

O cookie de sessao chama `AGENDAFACIL_SESSION`, usa `httpOnly=true`, `sameSite=strict` e timeout de 30 minutos.

O profile padrao `dev` usa `secure=false` para funcionar em HTTP local. Em producao, use `SPRING_PROFILES_ACTIVE=prod` e HTTPS; nesse profile o cookie fica `secure=true`.

## Protecao contra forca bruta no login

`LoginAttemptService` registra tentativas administrativas sem salvar senha. O sistema bloqueia temporariamente:

- 5 falhas para o mesmo e-mail em 15 minutos;
- 10 falhas para o mesmo IP em 15 minutos;
- bloqueio por 15 minutos.

O login usa mensagens genericas e nunca revela se o e-mail existe.

## Validacao de formularios

Forms publicos e administrativos usam Bean Validation como primeira barreira para campos obrigatorios, tamanhos maximos, telefone, datas, duracao e preco. As regras criticas continuam nos services.

Erros de validacao devem voltar como mensagens humanas e nao podem exibir stacktrace, nome de classe, SQL, payload ou enum cru.

## Testes de isolamento

O isolamento multi-tenant possui testes automatizados para impedir que um estabelecimento altere dados de outro por ID real. Novas operacoes administrativas devem manter consultas por `id + establishmentId`.
