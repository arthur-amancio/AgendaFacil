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
