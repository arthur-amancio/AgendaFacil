# Guia de estilo — AgendaFácil

A fundação P1.1 é compartilhada pelo login, fluxo público, painel e páginas de erro. Priorize leitura, previsibilidade e ações claras: fundo neutro, superfícies brancas, bordas discretas e azul reservado à ação principal e à seleção. Sem fontes remotas, gradientes decorativos, elevação animada ou framework CSS.

A fonte de verdade é `src/main/resources/static/css/app.css`. P1.1 estabelece componentes e shells; P1.2 aplica essa base ao fluxo público sem alterar rotas, regras de disponibilidade ou payloads.

## Tokens

| Uso | Token | Valor |
|---|---|---|
| Fundo | `--bg` | `#F6F8FA` |
| Superfície | `--surface` | `#FFFFFF` |
| Superfície sutil | `--surface-soft` | `#EEF2F6` |
| Borda de superfície | `--border` | `#DCE3EB` |
| Borda de controle | `--line-2` | `#8795A6` |
| Texto / secundário | `--text` / `--muted` | `#182230` / `#526174` |
| Primária / hover / sutil | `--primary` / `--primary-dark` / `--primary-soft` | `#245BC1` / `#1C4697` / `#EBF1FC` |
| Sucesso / texto / sutil | `--success` / `--success-dark` / `--success-soft` | `#187342` / `#14532D` / `#EDF8F0` |
| Aviso / texto / sutil | `--warning` / `--warning-dark` / `--warning-soft` | `#925500` / `#784500` / `#FFF6DF` |
| Erro / texto / sutil | `--danger` / `--danger-dark` / `--danger-soft` | `#BD3038` / `#94232A` / `#FDEFF0` |
| Foco / foco em fundo escuro | `--focus` / `--focus-on-dark` | `#245BC1` / `#B6D3FF` |
| Sidebar / item ativo | `--sidebar` / `--sidebar-active` | `#182230` / `#2B3E58` |

Aliases de compatibilidade: `--line` referencia `--border`; `--surface-2` referencia `--surface`; `--surface-3` referencia `--bg`; `--text-2` e `--muted-2` referenciam os textos principais. `--bg-2`, `--gray-soft`, `--accent*` e `--info*` reutilizam a paleta. Não criar outra paleta para uma tela.

## Tipografia

`--font` usa `ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif`. Inter não era carregada e não é uma dependência.

| Token | Tamanho / função |
|---|---|
| `--text-xs` | 0.75rem: badges, metadados e etapas compactas |
| `--text-sm` | 0.875rem: labels, auxiliares, tabelas e botões |
| `--text-base` | 1rem: corpo e campos de formulário |
| `--text-lg` | 1.125rem: introdução e marca |
| `--text-h3` | 1.25rem: subtítulos e cabeçalhos de seção |
| `--text-h2` | 1.5rem: título de seção padrão |
| `--text-h1` | `clamp(1.5rem, 2.4vw, 2rem)`: título de página |
| `--text-display` | `clamp(1.75rem, 4vw, 2.75rem)`: hero público |
| `--text-metric` | 2rem: valores das métricas |

Pesos `--weight-normal/medium/semibold/bold`: 400/500/600/700. Labels usam 500; botões e destaques usam 600; títulos e seleção ativa usam 700. Corpo com `--leading: 1.5`; títulos com `--leading-heading: 1.2`. Evitar texto auxiliar em negrito e pesos 850–950.

## Espaçamento, raios e sombras

Escala `--space-1/2/3/4/5/6/8/10/12`: 4/8/12/16/20/24/32/40/48px. Use 8–12px dentro de controles, 16–24px entre agrupamentos e 32px no shell desktop. Dimensões de controles e breakpoints não são espaçamentos.

Raios: `--radius-sm: 4px`, `--radius: 8px`, `--radius-lg: 12px`; `--radius-md` é alias do padrão. O nível maior aparece no login e modal. Círculos/pills ficam nos indicadores, badges e barra de progresso, não em todos os botões.

Apenas duas sombras: `--shadow: 0 2px 6px rgba(24,34,48,.04)` para superfícies e `--shadow-overlay: 0 16px 48px rgba(24,34,48,.18)` para modal. `--shadow-soft` é alias de `--shadow`. Agrupamentos internos como resumo, opções e horários não precisam de sombra. Transições de cor/borda usam `--transition: 140ms ease`.

## Botões e links

Use `.btn.primary` para a ação principal, `.btn.secondary` (ou `.light`) para ações secundárias e `.btn.danger`/`.danger-outline` para ações destrutivas. Os aliases `.btn-primary`, `.btn-secondary`, `.btn-danger` e `.btn-success` continuam disponíveis junto da base `.btn`. Variantes operacionais `.success-btn` e `.warning-btn` foram preservadas.

A base tem altura mínima de 44px, raio de 8px e peso 600. `.small` tem 36px em ponteiro preciso e 44px em ponteiro de toque. `.modal-close` tem 44×44px. Hover altera fundo/borda; active realça a borda; foco recebe outline. Disabled usa fundo neutro e cursor de indisponibilidade, inclusive no hover. `aria-disabled` só informa estado: não bloqueia uma ação por si; prefira `disabled` nativo nos botões.

`.link`, `.text-button` e `.back` mantêm sublinhado. Opções clicáveis também sublinham o texto no hover. Não acrescentar JavaScript para efeitos.

## Formulários

Labels visíveis, campos com fonte de 1rem, altura mínima de 44px, borda de controle e raio padrão. Inputs, selects e textareas têm `min-width: 0`; textarea permite redimensionamento vertical. Checkbox/radio nativo tem 20px; `.check-line` amplia a área do label para pelo menos 44px.

`label small` e `.helper-text` usam texto secundário e peso normal. `[aria-invalid="true"]` recebe borda de erro de 2px; `.field-error` é a base para a mensagem associada. Não marcar todos os campos obrigatórios como inválidos antes de interação. Campos desabilitados mantêm texto legível em superfície sutil.

`.form`, `.admin-form`, `.form-row` e `.form-grid` continuam como agrupadores. Filtros usam até quatro colunas e ações na linha seguinte. Nunca alterar names, bindings, validações, métodos ou CSRF para mudar a apresentação.

## Superfícies e feedback

`.card`, `.auth-card`, `.admin-card` e `.metric-card` usam superfície branca, borda e raio compartilhados. `.summary`, `.summary-card`, `.booking-summary` e `.settings-card` agrupam conteúdo relacionado; evite novos cards aninhados sem função. `.empty`/`.empty-state` usam borda tracejada e mensagem explícita.

Alertas: `.alert.success`/`.ok`, `.info`, `.warning` e `.danger`. Fundo semântico claro, texto escuro, borda, padding 12×16px e peso 500. Preserve `role="status"` para sucesso e `role="alert"` para erros. Mensagens continuam escapadas pelo Thymeleaf; nunca usar `th:utext`.

Badges mantêm labels em português fornecidos por `AppointmentViewUtil`: confirmado azul, pendente amarelo, concluído verde, falta vermelho, cancelado/expirado neutros. Cor não substitui o texto.

## Shells e navegação

- Login: card central de até 440px, marca AgendaFácil, título e formulário. Placeholder neutro e autocomplete apropriado. Contrato continua POST `/login`, `email`, `password` e CSRF.
- Público: `.shell` até 1080px; `.narrow` até 760px; hero com título fluido. `.public-booking` escopa os refinamentos do fluxo para não afetar login ou painel. Os seis passos e todas as URLs permanecem. Resumos usam grupos semânticos de label/valor, com data e horário destacados.
- Stepper: seis colunas compactas no desktop. Etapa atual tem borda inferior mais forte e `aria-current="step"`; concluídas têm borda tracejada e cor de sucesso. Até 700px aparece “Etapa X de 6”, nome sem truncamento obrigatório e progresso; a tela combinada usa “Etapas 3 e 4 de 6 — Data e horário”. O ícone de sucesso usa `.card > .done`, isolado de `.booking-stepper li.done`.
- Painel: sidebar escura de 248px acima de 900px, com rolagem própria se a altura for insuficiente. Item ativo tem borda lateral, peso e `aria-current="page"`. Conteúdo até 1440px dentro da área disponível; header sem card decorativo. As nove rotas administrativas permanecem.

## Foco e responsividade

Links, botões, inputs, selects e textareas usam outline de 2px com offset de 3px em `:focus-visible`; na sidebar o foco usa o token claro. `prefers-reduced-motion: reduce` desliga transições/animações e rolagem suave, inclusive a transição inline de alertas já existente em `app.js`.

Breakpoints mantidos: 1200px (grades densas/filtros), 1100px (dashboard/settings), 980px (topo legado), 900px (sidebar no fluxo e layout público empilhado), 700px (stepper compacto, forms em uma coluna e padding menor), 600px (ajustes de métricas/sidebar), 520px (navegação em duas colunas) e 460px (ações e horários em uma coluna). Entre 521–900px a navegação tem três colunas. A sidebar deixa de ser sobreposta/fixa nesses tamanhos.

Tabelas e agenda semanal mantêm overflow horizontal nos próprios contêineres. Não esconder overflow global para disfarçar problemas. Textos longos podem quebrar; grades públicas não impõem 230px quando o espaço disponível é menor. Alvos de QA: 1440×900, 1366×768, 768×1024, 390×844, 360×800 e 320×568.

## Fluxo público de agendamento

O fluxo público permanece server-rendered e funciona sem JavaScript. Cards de serviço e profissional são links inteiros, com nome, descrição ou bio, metadados e ação textual. Empty states usam mensagem explícita e `role="status"`.

Na página de horários, data e horário continuam na mesma rota e representam as etapas 3 e 4. O seletor de data é um formulário GET; horários disponíveis são links com ação textual e borda de disponibilidade, enquanto indisponíveis permanecem visíveis, usam `aria-disabled="true"` e apresentam o motivo. Cor nunca é o único indicador.

`.booking-summary` evolui junto das escolhas. No desktop, o resumo lateral permanece visível ao lado da ação; abaixo de 900px, o conteúdo principal vem primeiro e o resumo passa para baixo para não afastar o CTA. Resumos internos usam duas colunas quando há espaço e mantêm serviço/profissional em largura total. Data e horário recebem `.summary-item-priority` e `.summary-important`.

O formulário de dados preserva `customerName`, `customerPhone`, o honeypot `website`, campos ocultos e CSRF. Labels visíveis, auxiliares associados por `aria-describedby` e a mensagem de revisão reduzem ambiguidade. A confirmação mantém todos os campos ocultos e oferece retorno por link GET funcional, sem depender de `history.back()` nem colocar nome ou telefone na URL.

A tela de sucesso mostra status humano, próximo passo e resumo sem identificadores internos ou token público. O link de WhatsApp só existe quando fornecido pelo servidor e usa `target="_blank"` com `rel="noopener noreferrer"`.

## Compatibilidade e validação

As regras de `.panel`, `.top`, `.panel-shell`, `.metrics`, `.manage`, `.week` e aliases existentes foram mantidas. A busca nos templates atuais não encontrou `body.panel`, mas parte das classes é genérica; remover todo o bloco antigo seria uma limpeza adicional sem benefício necessário ao P1.1. Duplicações comprovadas de botões, campos e alertas foram consolidadas. Ainda há camadas históricas de layout, a tratar progressivamente.

`PresentationTemplatesTest` renderiza os 20 templates com dados determinísticos e verifica POST/CSRF, seis etapas, estados concluídos, sidebar, login e escape de mensagens. Isso não substitui QA visual nem testes de autenticação/banco. `mvn clean verify` continua sendo o gate completo; exige o ambiente compatível com os testes operacionais existentes (Linux, Docker e ferramentas Bash/POSIX).
