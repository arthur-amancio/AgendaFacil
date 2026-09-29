# Deploy operacional

Este guia descreve o deploy manual mínimo do AgendaFácil em um único servidor
Linux com Java 17, systemd e Caddy. Ele não publica nada automaticamente e não
substitui backup, monitoramento ou gestão do PostgreSQL.

## Topologia e pré-requisitos

- Caddy recebe HTTPS público nas portas 80/443 e encaminha somente para
  `127.0.0.1:8080`.
- A aplicação e o health interno escutam apenas em loopback. A porta 8081 nunca
  deve ser publicada pelo firewall ou pelo proxy.
- O PostgreSQL deve estar acessível somente pela aplicação e possuir backup
  operacional independente.
- O host precisa de um JRE/JDK Java 17 suportado, Caddy, systemd, `curl`,
  `grep` e `sha256sum`.

O layout esperado é:

```text
/opt/agendafacil/
  releases/<release-id>/
    agendafacil-pro.jar
    SHA256SUMS
  current -> /opt/agendafacil/releases/<release-id>
/etc/agendafacil/agendafacil.env
```

Diretórios de release e o link `current` devem pertencer a `root:root`. O
usuário de serviço `agendafacil` recebe apenas permissão de leitura e execução.
O arquivo de ambiente deve pertencer a `root:root`, com modo `0600`; não o
adicione ao Git nem o copie para o diretório da aplicação.

## Primeira instalação do servidor

Execute esta preparação uma única vez, antes de instalar a primeira release:

1. Instale um JRE ou JDK Java 17 suportado usando o método apropriado para a
   distribuição Linux escolhida. O projeto não instala nem exige um fornecedor
   específico. Valide a instalação:

   ```bash
   java -version
   test -x /usr/bin/java
   ```

   A saída de `java -version` deve indicar Java 17. A unit versionada usa
   `/usr/bin/java`; se a distribuição instalar o executável em outro caminho,
   revise e ajuste `ExecStart` conscientemente antes de instalar a unit.
2. Instale o Caddy pelo método oficial correspondente à distribuição Linux
   escolhida e valide o binário antes de continuar:

   ```bash
   caddy version
   ```

3. Crie o usuário de serviço sem login:

   ```bash
   sudo useradd --system --home-dir /nonexistent --shell /usr/sbin/nologin agendafacil
   ```

4. Crie os diretórios operacionais:

   ```bash
   sudo install -d -o root -g root -m 0755 /opt/agendafacil/releases
   sudo install -d -o root -g root -m 0700 /etc/agendafacil
   ```

5. Copie `ops/env/agendafacil.env.example` para
   `/etc/agendafacil/agendafacil.env`, preencha todos os valores e aplique
   `root:root`/`0600`. Não use aspas ou espaços desnecessários. A senha nunca
   deve aparecer em comandos, logs, tickets ou documentação.
6. Instale `ops/systemd/agendafacil.service` em
   `/etc/systemd/system/agendafacil.service` e valide com:

   ```bash
   sudo systemd-analyze verify /etc/systemd/system/agendafacil.service
   sudo systemctl daemon-reload
   ```

7. Configure o domínio real no DNS, com os registros adequados apontando para
   o servidor, e aguarde sua propagação antes de solicitar o certificado TLS.
8. Copie `ops/caddy/Caddyfile.example` para `/etc/caddy/Caddyfile`, substitua
   `agenda.example.com` pelo domínio real somente na cópia do servidor e valide:

   ```bash
   caddy validate --config /etc/caddy/Caddyfile
   ```

   O domínio fictício permanece no arquivo versionado para impedir publicação
   acidental de uma configuração específica de cliente.
9. No firewall, exponha somente SSH administrativo e 80/443. Bloqueie acesso
   externo a 8080, 8081 e ao PostgreSQL. Acesso remoto ao banco, se inevitável,
   deve ser limitado por origem e TLS.
10. Habilite o Caddy e a aplicação. O serviço da aplicação será iniciado após a
    instalação da primeira release:

    ```bash
    sudo systemctl enable --now caddy
    sudo systemctl enable agendafacil
    ```

## Instalação de uma release

O workflow de CI publica um artifact chamado
`agendafacil-pro-<commit-sha>` com apenas `agendafacil-pro.jar` e
`SHA256SUMS`. Baixe o artifact associado ao commit revisado; não reutilize um
JAR local ou de outra execução.

1. Escolha um `release-id` imutável, preferencialmente o SHA completo do commit,
   e copie os dois arquivos para `/opt/agendafacil/releases/<release-id>/`.
2. Verifique integridade antes de mudar o link ativo:

   ```bash
   cd /opt/agendafacil/releases/<release-id>
   sha256sum --check SHA256SUMS
   sudo chown -R root:root /opt/agendafacil/releases/<release-id>
   sudo chmod 0755 /opt/agendafacil/releases/<release-id>
   sudo chmod 0644 agendafacil-pro.jar SHA256SUMS
   ```

3. Confirme o backup recente do PostgreSQL e revise as migrations incluídas na
   release. O Flyway executa migrations no startup; interrompa o deploy se o
   preflight da release não estiver concluído.
4. Aponte atomicamente `current` para a nova release e reinicie o serviço:

   ```bash
   sudo ln -sfn /opt/agendafacil/releases/<release-id> /opt/agendafacil/current
   sudo systemctl restart agendafacil
   ```

O systemd envia `SIGTERM` e espera até 30 segundos. Isso deixa margem para o
graceful shutdown de 20 segundos configurado na aplicação.

## Validação e observação

Verifique primeiro o processo e os logs, sem imprimir variáveis de ambiente:

```bash
sudo systemctl status agendafacil --no-pager
sudo journalctl -u agendafacil --since "10 minutes ago" --no-pager
curl --fail --silent http://127.0.0.1:8081/actuator/health/liveness

readiness_url=http://127.0.0.1:8081/actuator/health/readiness
readiness_timeout_seconds=60
readiness_poll_seconds=2
readiness_deadline=$((SECONDS + readiness_timeout_seconds))
readiness_up=false

while (( SECONDS < readiness_deadline )); do
  if readiness_response="$(curl --fail --silent --max-time "$readiness_poll_seconds" "$readiness_url" 2>/dev/null)" \
    && grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<< "$readiness_response"; then
    readiness_up=true
    break
  fi
  sleep "$readiness_poll_seconds"
done

if [[ "$readiness_up" != true ]]; then
  echo "ERRO: a aplicação não ficou pronta em ${readiness_timeout_seconds}s." >&2
  exit 1
fi

echo "Readiness UP. Prosseguindo para o smoke test HTTPS."
curl --fail --silent --show-error https://agenda.example.com/login >/dev/null
```

O bloco acima deve ser executado com Bash. Ele tenta readiness por no máximo 60
segundos, em intervalos de 2 segundos, e interrompe o procedimento com código
diferente de zero se não receber HTTP de sucesso com `status` igual a `UP`.
Somente considere a release pronta quando readiness estiver `UP`, o acesso
HTTPS público responder e um smoke test autenticado e um agendamento público de
teste estiverem corretos. A porta 8081 é para consulta local, nunca para Caddy.

## Rollback

Mantenha ao menos a release anterior. Se a validação falhar:

1. inspecione os logs sem copiar segredos;
2. reapresente o link `current` à release anterior;
3. reinicie e repita liveness, readiness e smoke tests.

```bash
sudo ln -sfn /opt/agendafacil/releases/<release-id-anterior> /opt/agendafacil/current
sudo systemctl restart agendafacil
```

Rollback do JAR não desfaz migrations. Antes de voltar uma versão após mudança
de schema, confirme que a versão anterior é compatível com o banco atual ou
restaure um backup seguindo um procedimento previamente testado.
