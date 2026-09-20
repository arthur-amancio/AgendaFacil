-- V1 e V5 criaram dados de demonstracao antes de existir separacao por ambiente.
-- Mantemos essas migrations imutaveis e neutralizamos o acesso em uma migration aditiva.
UPDATE users_app
SET enabled = false,
    password_hash = '!disabled-demo-account!'
WHERE LOWER(email) = 'admin@demo.local'
   OR password_hash = '$2y$10$SSqTDKeVQzAepUbciVQu0.XRLXRUm2BXy7FY.sZtqoo2l1fLKC3tK'
   OR establishment_id IN (
       SELECT id
       FROM establishments
       WHERE slug = 'agenda-demo'
   );

UPDATE establishments
SET active = false
WHERE slug = 'agenda-demo';
