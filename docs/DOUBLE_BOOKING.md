# Protecao contra double booking

## Garantia no PostgreSQL

A migration V8 instala `btree_gist` e cria a exclusion constraint
`ex_appointments_no_blocking_overlap`. Ela compara, por estabelecimento e
profissional, o intervalo `tsrange(start_at, end_at, '[)')` dos agendamentos com
status `CONFIRMED` ou `PENDING_APPROVAL`.

O formato `[inicio, fim)` permite horarios adjacentes e rejeita sobreposicao
parcial ou total, inclusive com duracoes diferentes. O PostgreSQL tambem resolve
inserts concorrentes: uma transacao aguarda a outra e uma delas recebe SQLSTATE
`23P01` se os intervalos conflitarem.

Uma restricao `UNIQUE(professional_id, start_at)` nao seria suficiente porque
nao detecta intervalos que comecam em horarios diferentes e se sobrepoem.

## Pendencias expiradas

A constraint nao usa relogio nem configuracao de expiracao. Predicados de indice
precisam ser imutaveis e nao podem consultar `establishment_settings`. Por isso,
`PENDING_APPROVAL` bloqueia enquanto esse for o status persistido.

Antes de criar um agendamento, o backend converte pendencias vencidas para
`EXPIRED` usando `pending_expiration_minutes` do estabelecimento e executa
`flush` antes do novo insert. `EXPIRED` nao participa da constraint.

## Tratamento na aplicacao

Os inserts usam `saveAndFlush`, fazendo a violacao aparecer dentro do metodo
transacional. SQLSTATE `23P01` ou o nome da constraint sao traduzidos para:

> Esse horario acabou de ser reservado. Escolha outro horario.

A excecao de negocio sai do metodo transacional e provoca rollback completo. Ela
nao e engolida dentro de uma transacao marcada para rollback, evitando retorno
de sucesso ou `UnexpectedRollbackException` tardia.

## Preflight antes da V8

A V8 nao apaga nem altera agendamentos. Ela falha antes de criar as constraints
se houver periodo invalido ou sobreposicao bloqueante existente.

Execute em backup ou replica do banco alvo:

```sql
SELECT id, establishment_id, professional_id, start_at, end_at, status
FROM appointments
WHERE start_at >= end_at
ORDER BY establishment_id, professional_id, start_at;

SELECT
  a.id AS appointment_a,
  b.id AS appointment_b,
  a.establishment_id,
  a.professional_id,
  a.start_at AS a_start,
  a.end_at AS a_end,
  b.start_at AS b_start,
  b.end_at AS b_end,
  a.status AS a_status,
  b.status AS b_status
FROM appointments a
JOIN appointments b
  ON a.id < b.id
 AND a.establishment_id = b.establishment_id
 AND a.professional_id = b.professional_id
 AND a.start_at < b.end_at
 AND a.end_at > b.start_at
WHERE a.status IN ('CONFIRMED', 'PENDING_APPROVAL')
  AND b.status IN ('CONFIRMED', 'PENDING_APPROVAL')
ORDER BY a.establishment_id, a.professional_id, a.start_at;
```

Se qualquer consulta retornar linhas, interrompa o deploy. Faca backup e resolva
cada caso por decisao operacional, normalmente cancelando, recusando ou expirando
o registro incorreto sem apagar o historico. Depois, execute novamente o
preflight e somente entao permita que o Flyway aplique a V8.
