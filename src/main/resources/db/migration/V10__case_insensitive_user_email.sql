DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM users_app
    GROUP BY LOWER(email)
    HAVING COUNT(*) > 1
  ) THEN
    RAISE EXCEPTION 'case-insensitive duplicate user emails exist; reconcile them before applying V10';
  END IF;
END
$$;

CREATE UNIQUE INDEX uk_users_app_email_ci
  ON users_app (LOWER(email));

-- V1 inseriu o estabelecimento demo com ID explicito e nao avancou a sequence.
-- Alinha somente o proximo valor para que o primeiro tenant real nao reutilize o ID 1.
SELECT setval(
  pg_get_serial_sequence('establishments', 'id'),
  COALESCE(MAX(id), 1),
  MAX(id) IS NOT NULL
)
FROM establishments;
