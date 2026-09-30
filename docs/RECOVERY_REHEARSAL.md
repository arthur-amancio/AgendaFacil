# Recovery rehearsal descartável

Este documento descreve o rehearsal automatizado da cadeia de backup e
recuperação do AgendaFácil. Ele prova tecnicamente, com dados sintéticos, que os
scripts versionados conseguem produzir e restaurar um backup. Não configura nem
comprova armazenamento off-site real e não torna o produto pronto para produção
ou para piloto.

## Topologia do CI

Cada execução cria somente recursos efêmeros no runner do GitHub Actions:

1. um container `postgres:16-alpine` para o database source;
2. o JAR real preparado pelo pipeline, iniciado com profile `prod`, aplica e
   valida as migrations Flyway no source;
3. uma fixture SQL exclusivamente sintética adiciona um tenant, catálogo,
   cliente, appointment e um canário determinístico;
4. `ops/backup/backup-postgres.sh` executa o backup real do P0.6.7;
5. o bundle cifrado e seu checksum atravessam uma **simulated off-site
   boundary**, representada por diretório efêmero separado;
6. a repository local original é removida;
7. um segundo container `postgres:16-alpine`, separado e vazio, recebe o restore
   executado por `ops/backup/restore-postgres.sh`;
8. dados source/restored são comparados e o mesmo JAR inicia contra o recovery;
9. `/login`, readiness e a página pública tenant-safe do canário são verificadas;
10. containers, keyrings, bundle e demais temporários são destruídos pelo trap.

O runner usa clientes PostgreSQL 16. O script falha antes do rehearsal se
`pg_dump`, `pg_restore` ou `psql` não pertencerem ao major 16. Docker é requisito
somente desta validação descartável do CI, não do servidor de produção.

## Chave OpenPGP efêmera

O rehearsal gera uma chave temporária sem passphrase e com expiração curta. O
keyring de recovery guarda a chave privada; somente a chave pública é exportada
por stream para o keyring de backup. O fingerprint completo é usado na execução,
mas seu valor não é impresso nem persistido.

O próprio cenário comprova que o backup recusa um keyring que contenha a chave
privada do recipient. Todo material OpenPGP fica dentro do diretório temporário
do runner e é apagado ao final, inclusive em falhas.

## Backup e simulated off-site boundary

O script real produz custom archive, valida sua estrutura, criptografa e calcula
SHA-256. O rehearsal confirma que o bundle possui somente `.dump.gpg` e
`.dump.gpg.sha256`, que o checksum é válido e que nenhum `.dump` plaintext foi
publicado.

Somente esses dois arquivos são copiados para a simulated off-site boundary. O
checksum é verificado novamente no destino. Depois disso, a repository local de
origem é descartada e todas as etapas seguintes usam exclusivamente o bundle
recuperado dessa fronteira.

Essa separação valida o mecanismo de transferência/recuperação dentro do runner,
mas **não comprova off-site real**, durabilidade, controle de acesso, retenção ou
disponibilidade de um fornecedor externo.

## Restore, integridade e smoke

O recovery PostgreSQL começa vazio. `restore-postgres.sh` valida confirmação do
database, checksum, estrutura do archive e restaura em transação única. Suas
validações de Flyway, tabelas principais e consultas básicas permanecem ativas.

Após o restore, o rehearsal compara:

- valor completo do canário sintético;
- quantidade de estabelecimentos;
- quantidade de appointments;
- quantidade de registros de `flyway_schema_history`.

O JAR inicia novamente com profile `prod`, credenciais efêmeras, timezone
explícito e portas locais. O cenário exige readiness `UP`, resposta de `/login`
e leitura do estabelecimento restaurado por `/agenda/recovery-rehearsal-canary`.
Também confirma que o startup não adicionou uma migration inesperada.

## Verificações fail-closed

Antes do restore bem-sucedido, o database recovery continua vazio enquanto são
validados estes casos negativos:

- checksum adulterado;
- `RESTORE_CONFIRM_DATABASE` diferente de `PGDATABASE`;
- database alvo não vazio;
- bundle sem checksum;
- keyring sem chave privada;
- host de backup contendo a chave privada do recipient.

Cada caso precisa retornar código diferente de zero. As proteções do P0.6.7 não
são desativadas nem substituídas por lógica específica de teste.

## Execução e relatório

O workflow preserva `mvn clean verify`, prepara `dist/agendafacil-pro.jar` e
`SHA256SUMS`, executa o rehearsal e só então publica o mesmo artifact do P0.6.6.
Nenhum dump, checksum de database, keyring ou chave é enviado como artifact.

Para uma execução local equivalente são necessários Java 17, Docker, GnuPG,
OpenSSL, curl e clientes PostgreSQL 16:

```bash
POSTGRES_CLIENT_BIN=/usr/lib/postgresql/16/bin \
  ops/recovery/run-recovery-rehearsal.sh dist/agendafacil-pro.jar
```

Os logs e o GitHub Step Summary registram:

- início e conclusão UTC do backup;
- duração do backup em segundos;
- início e conclusão UTC do recovery;
- duração total do recovery em segundos;
- bundle usado, contagens comparadas, digest do canário, smoke e readiness.

As medições descrevem somente o runner descartável daquela execução. Um tempo
inferior a quatro horas não garante RTO de produção. Da mesma forma, o rehearsal
não comprova RPO de 24 horas porque timer e transferência off-site reais ainda
não estão operacionais.

## Bloqueadores antes do piloto real

Mesmo com o CI verde, continuam obrigatórios:

1. escolher e configurar um destino off-site independente;
2. executar e observar a transferência real de bundles cifrados e checksums;
3. recuperar um bundle diretamente desse destino real;
4. completar restore na infraestrutura final e medir o RPO/RTO real;
5. definir responsabilidade, frequência do exercício e resposta a falhas.

Até essas etapas serem concluídas, o rehearsal é evidência técnica da cadeia,
não prova de prontidão operacional do piloto.
