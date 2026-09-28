# AgendaFácil Pro

![Java 17](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)

Sistema web de agendamento para pequenos estabelecimentos e profissionais de serviços. O cliente agenda sem criar conta, enquanto o estabelecimento administra os atendimentos em um painel protegido.

> **Status:** projeto de portfólio em desenvolvimento.

## Funcionalidades

- página pública de agendamento;
- painel administrativo autenticado;
- cadastro e organização de horários;
- persistência com PostgreSQL;
- validação de dados;
- proteção com Spring Security e CSRF;
- versionamento do banco com Flyway;
- dados de demonstração para desenvolvimento local;
- workflow de integração contínua no GitHub Actions.

## Stack

- Java 17
- Spring Boot 4.1.1
- Spring MVC e Thymeleaf
- Spring Security
- Spring Data JPA
- PostgreSQL e Flyway
- Maven
- Docker Compose

## Como executar

~~~bash
git clone https://github.com/arthur-amancio/AgendaFacil.git
cd AgendaFacil
cp .env.example .env
~~~

Defina um valor não vazio para DB_PASSWORD no arquivo .env. Em seguida, inicie o banco:

~~~bash
docker compose up -d
~~~

Use a mesma senha ao iniciar a aplicação.

Linux/macOS:

~~~bash
export DB_PASSWORD="<mesmo valor definido no .env>"
export SPRING_PROFILES_ACTIVE="dev"
mvn spring-boot:run
~~~

PowerShell:

~~~powershell
$env:DB_PASSWORD="<mesmo valor definido no .env>"
$env:SPRING_PROFILES_ACTIVE="dev"
mvn spring-boot:run
~~~

O profile `dev` e obrigatorio para usar os defaults locais. A aplicacao nao ativa
nenhum ambiente implicitamente. Para habilitar o tenant de demonstracao no banco
local, use explicitamente `dev,demo`. Qualquer outra combinacao, inclusive `demo`
isolado ou junto de `prod`, mantem a credencial historica desativada.

Acesse:

- agenda pública demo, quando iniciado com `dev,demo`: http://localhost:8080/agenda/agenda-demo
- painel: http://localhost:8080/panel

## Variáveis de ambiente

| Variável | Descrição |
|---|---|
| DB_URL | URL JDBC do PostgreSQL |
| DB_USERNAME | usuário do banco |
| DB_PASSWORD | senha do banco; obrigatória e sem fallback em produção |
| APP_TIME_ZONE | timezone da aplicação e da sessão PostgreSQL; obrigatório em produção |
| SPRING_PROFILES_ACTIVE | use `dev` localmente, `dev,demo` apenas para demo local e `prod` em produção |

Em producao, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e `APP_TIME_ZONE` devem ser
fornecidos explicitamente antes de iniciar com `SPRING_PROFILES_ACTIVE=prod`.
Valores ausentes, vazios ou timezone invalido recusam o startup. Os profiles
`prod,dev`, `prod,demo` e provisioning sem `prod` tambem sao recusados.

Em producao, a aplicacao escuta somente em `127.0.0.1` e deve receber trafego
exclusivamente de um proxy reverso local. Esse proxy deve remover quaisquer
headers `X-Forwarded-*` enviados pelo cliente e escrever seus proprios
`X-Forwarded-For`, `X-Forwarded-Proto` e, quando aplicavel,
`X-Forwarded-Host`. O unico proxy confiavel pela aplicacao e
`127.0.0.1/32`; conexoes de outras origens nao podem controlar o IP remoto nem
marcar uma requisicao HTTP como HTTPS.

O arquivo .env é ignorado pelo Git. Não publique senhas reais no código, nas migrations ou na documentação.

## Autor

Desenvolvido por [Arthur Amancio Francisco](https://www.linkedin.com/in/arthur-amancio-francisco/) como projeto de estudo e portfólio.
