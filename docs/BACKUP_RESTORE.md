# Backup e recuperação do PostgreSQL

Este runbook define o procedimento mínimo de backup e restore do AgendaFácil
para o piloto. Backup não é o artifact da aplicação: o JAR permite executar uma
versão do código, enquanto este processo preserva os dados do PostgreSQL.

## Arquitetura e limites

- O backup é lógico, de um único database, produzido por `pg_dump` 16 em
  formato custom.
- Cada execução valida o archive com `pg_restore --list`, criptografa-o com uma
  chave pública OpenPGP e calcula SHA-256 sobre o arquivo já cifrado.
- Somente o bundle completo é publicado no repositório. Arquivos temporários
  ficam em `RuntimeDirectory` privado e são removidos também quando há erro.
- A chave privada fica fora do servidor de produção. O script recusa executar
  se encontrar material privado para o recipient no `GNUPGHOME` do backup.
- A retenção local mantém 14 backups diários, 8 semanais e 6 mensais. O primeiro
  backup bem-sucedido de cada semana ISO e de cada mês vira o snapshot daquele
  período; uma nova execução no mesmo período não o sobrescreve.
- A unit roda diariamente às 06:00 UTC. `Persistent=true` faz o systemd executar
  uma rodada perdida quando o host voltar, sem prometer um horário comercial.

O alvo operacional inicial é **RPO de até 24 horas** e **RTO de até 4 horas**.
São objetivos, não garantias: só um exercício periódico, com medição, comprova
que o ambiente real os atende.

`pg_dump` de um database não inclui roles, tablespaces, configuração do servidor,
arquivos de ambiente, configuração systemd/Caddy, artifacts do CI nem arquivos
externos ao PostgreSQL. Roles, tablespaces e opções do serviço gerenciado devem
ser recriados pelos mecanismos do fornecedor real; não se usa `pg_dumpall` com
hashes de senha como atalho. Os demais itens precisam de gestão separada.

Também não substituem backup: o repositório GitHub, o artifact JAR do GitHub
Actions, migrations Flyway, um snapshot isolado do VPS ou o banco local de
desenvolvimento. Migrations reconstroem schema; não reconstroem dados dos clientes.

## Pré-requisitos

No host de backup instale, pelo método oficial da distribuição:

- clientes PostgreSQL **16** (`pg_dump`, `pg_restore` e `psql`);
- GnuPG, `sha256sum` e Bash;
- uma conta de banco dedicada, com apenas os privilégios necessários para ler
  integralmente o database do AgendaFácil.

Valide explicitamente:

```bash
pg_dump --version
pg_restore --version
psql --version
gpg --version
sha256sum --version
```

Os três clientes PostgreSQL usados por estes scripts devem indicar major 16.
Antes de atualizar o servidor PostgreSQL, revise esta trava e a compatibilidade
do formato; não deixe um cliente antigo fazer backup de servidor mais novo.

## Custódia da chave OpenPGP

1. Em uma estação de recuperação confiável e fora do servidor de produção,
   gere ou selecione uma chave OpenPGP dedicada a backup.
2. Guarde a chave privada e sua revogação em armazenamento offline protegido e
   testado. Sem a chave privada, o backup não é recuperável.
3. Exporte somente a chave pública e importe-a no `GNUPGHOME` do host de backup.
4. Compare por um canal independente o fingerprint completo exibido por:

   ```bash
   gpg --with-colons --fingerprint backup@example.invalid
   ```

5. Configure em `BACKUP_GPG_RECIPIENT` o fingerprint completo de 40 ou 64
   caracteres, nunca um nome, e-mail ou key ID curto.

Não copie a chave privada para produção, para o Git, para tickets ou para o
repositório de backups. A chave pública não é segredo, mas sua autenticidade é
crítica: trocar o recipient sem validação pode tornar todos os novos backups
inacessíveis ao operador legítimo.

## Primeira instalação do backup

Crie um usuário dedicado, os diretórios privados e instale os arquivos
versionados como `root`:

```bash
sudo useradd --system --home-dir /var/lib/agendafacil-backup --shell /usr/sbin/nologin agendafacil-backup
sudo install -d -o root -g root -m 0700 /etc/agendafacil
sudo install -d -o root -g root -m 0755 /usr/local/libexec/agendafacil
sudo install -o root -g root -m 0755 ops/backup/backup-postgres.sh /usr/local/libexec/agendafacil/
sudo install -o root -g root -m 0755 ops/backup/backup-retention.sh /usr/local/libexec/agendafacil/
sudo install -o root -g root -m 0755 ops/backup/restore-postgres.sh /usr/local/libexec/agendafacil/
sudo install -o root -g root -m 0644 ops/systemd/agendafacil-backup.service /etc/systemd/system/
sudo install -o root -g root -m 0644 ops/systemd/agendafacil-backup.timer /etc/systemd/system/
sudo install -o root -g root -m 0600 ops/backup/agendafacil-backup.env.example /etc/agendafacil/backup.env
```

Preencha `/etc/agendafacil/backup.env` sem registrar valores em shell history.
Ele deve permanecer `root:root` e `0600`. Use TLS compatível com o banco real em
`PGSSLMODE`; não coloque a senha em argumentos de processo. Prepare o keyring e
importe somente a chave pública por um caminho temporário protegido:

```bash
sudo install -d -o agendafacil-backup -g agendafacil-backup -m 0700 /var/lib/agendafacil-backup/gnupg
sudo -u agendafacil-backup env GNUPGHOME=/var/lib/agendafacil-backup/gnupg \
  gpg --batch --import /caminho/protegido/chave-publica.asc
```

Remova a cópia temporária após conferir o fingerprint. O diretório do keyring
deve permanecer de propriedade de `agendafacil-backup`, com modo `0700`.

Valide e habilite o timer:

```bash
sudo systemd-analyze verify /etc/systemd/system/agendafacil-backup.service /etc/systemd/system/agendafacil-backup.timer
sudo systemctl daemon-reload
sudo systemctl enable --now agendafacil-backup.timer
systemctl list-timers agendafacil-backup.timer
```

## Execução e verificação

Para um primeiro teste controlado:

```bash
sudo systemctl start agendafacil-backup.service
sudo systemctl status agendafacil-backup.service --no-pager
sudo journalctl -u agendafacil-backup.service --since "30 minutes ago" --no-pager
```

Uma execução válida termina com um diretório como:

```text
/var/lib/agendafacil-backup/repository/daily/agendafacil-20260929T060000Z/
  agendafacil-20260929T060000Z.dump.gpg
  agendafacil-20260929T060000Z.dump.gpg.sha256
```

Confirme o checksum dentro do bundle sem descriptografá-lo:

```bash
cd /var/lib/agendafacil-backup/repository/daily/agendafacil-<UTC>
sha256sum --check agendafacil-<UTC>.dump.gpg.sha256
```

O arquivo cifrado e seu checksum devem ser copiados juntos para armazenamento
off-site com acesso restrito, versionamento/imutabilidade e política de retenção
equivalente ou superior. Nunca envie a chave privada junto. A retenção local não
substitui a cópia off-site, e sincronização não testada não é backup verificado.
Depois de cada transferência, execute `sha256sum --check` no destino off-site.

Antes do primeiro piloto real é obrigatório escolher esse destino independente,
automatizar ou operacionalizar a cópia dos bundles cifrados, verificar o checksum
no destino, preservar 14/8/6, recuperar um bundle a partir dele e concluir um
restore completo. O P0.6.7 prepara os arquivos; não configura storage off-site.

## Restore fail-closed em banco novo

Um restore é uma operação administrativa excepcional. Faça-o primeiro em host
de recuperação isolado e em **banco novo e vazio**. Não aponte o script ao
database em uso pela aplicação e não use `--clean`: ele deliberadamente recusa
qualquer alvo que já contenha tabelas de usuário.

1. Escolha um bundle, copie também seu `.sha256` e valide a origem.
2. Crie um database vazio, com encoding/locale adequados, e conceda ao usuário
   de restore propriedade e permissão para criar objetos/extensões requeridos.
3. Em um `GNUPGHOME` privado (`0700`) da estação de recuperação, importe a chave
   privada custodiada. Remova-a da estação temporária após o exercício.
4. Exporte as variáveis libpq `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`,
   `PGPASSWORD` e `PGSSLMODE`. Defina `RESTORE_CONFIRM_DATABASE` com exatamente
   o mesmo nome de `PGDATABASE`; isso é uma confirmação explícita, não uma forma
   de escolher o alvo.
5. Execute o script a partir de uma cópia revisada da mesma release:

   ```bash
   export GNUPGHOME=/caminho/privado/gnupg-restore
   export RESTORE_CONFIRM_DATABASE="$PGDATABASE"
   /usr/local/libexec/agendafacil/restore-postgres.sh /caminho/do/bundle
   ```

O script, nesta ordem, valida checksum, recusa banco não vazio, verifica se a
chave consegue listar o archive pelo stream e só então restaura. `pipefail`,
`--exit-on-error` e `--single-transaction` impedem sucesso aparente ou restore
parcial; `--no-owner` e `--no-acl` evitam depender de roles do host original.

Depois do restore:

1. confirme que o script validou `flyway_schema_history`, migrations com sucesso,
   tabelas principais e consultas básicas;
2. inicie uma instância isolada da aplicação apontando somente para o banco
   restaurado e execute login, agenda pública e leitura tenant-safe;
3. registre início/fim, bundle UTC, checksum, resultado e responsável, sem
   copiar senha ou chave privada;
4. descarte com segurança o ambiente temporário e revise o RPO/RTO medido.

Um exercício de restore deve ocorrer antes do piloto e depois periodicamente.
O backup diário só pode ser considerado operacional após ao menos um restore
completo e documentado.

## Falhas e resposta operacional

- Se a unit falhar, preserve o último bundle válido, consulte o journal e corrija
  a causa; não desative checksum, criptografia ou validações para “fazer passar”.
- Falha de `pg_dump` ou `pg_restore --list` não publica bundle.
- Falha de GPG, checksum ou retenção deixa a execução com exit code não zero.
- Se a chave pública for perdida/trocada, interrompa novas execuções até validar
  o fingerprint correto. Rotação deve manter a chave privada antiga enquanto
  existirem backups cifrados para ela.
- Se o restore recusar o database por não estar vazio, crie outro database; não
  edite o script nem apague objetos do alvo para contornar o guard.

Alertas automáticos, exportação off-site automatizada, métricas e exercícios
agendados pertencem ao hardening operacional posterior (P0.6.8). Até lá, o
responsável pelo piloto deve verificar diariamente o timer, o journal e a idade
do último bundle válido.
