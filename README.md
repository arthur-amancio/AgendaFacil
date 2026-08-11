# AgendaFácil Pro

![Java 17](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?logo=springboot&logoColor=white)
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
- Spring Boot 3.3
- Spring MVC e Thymeleaf
- Spring Security
- Spring Data JPA
- PostgreSQL e Flyway
- Maven
- Docker Compose

## Como executar

~~~bash
git clone https://github.com/ArthurFancisco/AgendaFacil.git
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
mvn spring-boot:run
~~~

PowerShell:

~~~powershell
$env:DB_PASSWORD="<mesmo valor definido no .env>"
mvn spring-boot:run
~~~

Acesse:

- agenda pública: http://localhost:8080/agenda/agenda-demo
- painel: http://localhost:8080/panel

## Variáveis de ambiente

| Variável | Descrição |
|---|---|
| DB_URL | URL JDBC do PostgreSQL |
| DB_USERNAME | usuário do banco |
| DB_PASSWORD | senha do banco, obrigatória |

O arquivo .env é ignorado pelo Git. Não publique senhas reais no código, nas migrations ou na documentação.

## Autor

Desenvolvido por [Arthur Amancio Francisco](https://www.linkedin.com/in/arthur-amancio-francisco/) como projeto de estudo e portfólio.
