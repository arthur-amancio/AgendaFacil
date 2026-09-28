# Provisionamento operacional de tenants

Este runbook é exclusivo para operadores do AgendaFácil. O cliente não executa o comando e não recebe acesso ao código-fonte ou ao banco.

O provisioning é um job one-shot sem servidor HTTP. Ele só existe quando as três guardas estão presentes ao mesmo tempo:

1. profile `provisioning`;
2. `app.provisioning.enabled=true`;
3. comando explícito `create-tenant` ou `activate-tenant`.

Profile ou flag isolados não executam operações. Nunca inclua o profile `provisioning` no startup normal do serviço web.

## Preflight do banco

Antes do deploy da V10, verifique e-mails que diferem apenas por maiúsculas/minúsculas:

```sql
SELECT lower(email), count(*), array_agg(id ORDER BY id)
FROM users_app
GROUP BY lower(email)
HAVING count(*) > 1;
```

Se houver resultado, interrompa o deploy e reconcilie os usuários manualmente. A migration não escolhe, exclui ou altera e-mails.

## Pré-requisitos operacionais

- usar o mesmo artefato aprovado que executa a aplicação;
- fornecer `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e `APP_TIME_ZONE` pelos mecanismos seguros do ambiente;
- executar em terminal administrativo controlado, sem ingress HTTP;
- usar `SPRING_PROFILES_ACTIVE=prod,provisioning`;
- confirmar que o slug e o e-mail ainda não existem;
- não habilitar logs de parâmetros SQL/Hibernate durante a operação.

## Criar tenant

Exemplo com valores fictícios:

```bash
java -jar target/agendafacil-pro-1.0.0.jar \
  --spring.profiles.active=prod,provisioning \
  --app.provisioning.enabled=true \
  --app.provisioning.command=create-tenant \
  --app.provisioning.name="Estabelecimento Piloto" \
  --app.provisioning.slug=estabelecimento-piloto \
  --app.provisioning.confirm-slug=estabelecimento-piloto \
  --app.provisioning.whatsapp=5517999999999 \
  --app.provisioning.city="Cidade/UF" \
  --app.provisioning.description="Atendimento com horário marcado." \
  --app.provisioning.owner-name="Nome do responsável" \
  --app.provisioning.owner-email=owner@example.invalid
```

O comando exige:

- nome do estabelecimento;
- slug canônico em lowercase;
- WhatsApp brasileiro com DDD;
- nome do OWNER;
- e-mail do OWNER.

Cidade e descrição são opcionais. O comando não cria serviços, profissionais, clientes, agendamentos ou dados de demonstração.

Em um terminal interativo, a senha forte inicial é exibida uma única vez depois do commit. Ela não é temporária: deve ser importada imediatamente em um gerenciador de senhas e entregue ao OWNER por canal seguro.

### Execução sem console interativo

Não redirecione stdout para capturar a senha. Em ambiente sem console, informe um caminho absoluto para um arquivo que ainda não exista:

```bash
  --app.provisioning.password-output-file=/run/secrets/agendafacil-owner-piloto
```

O filesystem precisa suportar permissões POSIX. O arquivo é criado como `0600` somente depois do commit. Importe a credencial no gerenciador de senhas, confirme a entrega e remova o arquivo do ambiente operacional.

Nunca forneça senha por argumento, variável de ambiente, migration ou GitHub Actions.

## Estado após create-tenant

A operação é atômica e cria:

- estabelecimento com `active=false`;
- configurações oficiais da aplicação;
- sete dias fechados, sem horários preenchidos;
- OWNER habilitado com hash BCrypt.

O OWNER consegue entrar no painel enquanto a página pública permanece indisponível. Antes da ativação, ele deve:

1. configurar os horários semanais;
2. cadastrar pelo menos um serviço ativo;
3. cadastrar pelo menos um profissional ativo;
4. vincular o profissional ao serviço;
5. revisar settings e conteúdo do estabelecimento.

## Ativar tenant

Depois da configuração:

```bash
java -jar target/agendafacil-pro-1.0.0.jar \
  --spring.profiles.active=prod,provisioning \
  --app.provisioning.enabled=true \
  --app.provisioning.command=activate-tenant \
  --app.provisioning.slug=estabelecimento-piloto \
  --app.provisioning.confirm-slug=estabelecimento-piloto
```

A ativação falha sem modificar o tenant se faltar qualquer item:

- estabelecimento existente e ainda inativo;
- settings;
- exatamente sete dias de funcionamento;
- pelo menos um dia aberto e válido;
- pelo menos um OWNER habilitado;
- serviço ativo;
- profissional ativo;
- vínculo entre profissional ativo e serviço ativo.

O comando nunca cria ou corrige catálogo/horários automaticamente.

## Falhas e repetição

- exit code `0`: operação concluída;
- exit code diferente de zero: operação recusada ou falhou;
- mesmo slug ou e-mail: falha explícita, sem update/upsert;
- segunda execução de `create-tenant`: falha explícita;
- falha durante a transação: estabelecimento, settings, horários e OWNER sofrem rollback;
- não tente “completar” manualmente um tenant parcial sem diagnóstico e backup.

Se a criação confirmar commit, mas a publicação da senha falhar, não repita `create-tenant`: o tenant já existe e precisa de intervenção operacional para redefinir a credencial.

Os únicos eventos de sucesso registrados são `TENANT_PROVISIONED` e `TENANT_ACTIVATED`, contendo IDs, slug e timestamp — nunca senha, hash, telefone, nome ou e-mail.
