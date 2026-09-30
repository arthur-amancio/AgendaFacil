BEGIN;

INSERT INTO establishments (
  id, name, slug, whatsapp, city, description, active
) VALUES (
  900001,
  'Recovery Rehearsal Studio',
  'recovery-rehearsal-canary',
  '5517900000001',
  'Cidade Sintética',
  'RECOVERY_REHEARSAL_CANARY_V1',
  TRUE
);

INSERT INTO establishment_settings (establishment_id)
VALUES (900001);

INSERT INTO establishment_business_hours (
  establishment_id, day_of_week, is_open, opening_time, closing_time
) VALUES
  (900001, 'MONDAY',    TRUE, TIME '08:00', TIME '18:00'),
  (900001, 'TUESDAY',   TRUE, TIME '08:00', TIME '18:00'),
  (900001, 'WEDNESDAY', TRUE, TIME '08:00', TIME '18:00'),
  (900001, 'THURSDAY',  TRUE, TIME '08:00', TIME '18:00'),
  (900001, 'FRIDAY',    TRUE, TIME '08:00', TIME '18:00'),
  (900001, 'SATURDAY',  TRUE, TIME '08:00', TIME '13:00'),
  (900001, 'SUNDAY',    FALSE, NULL, NULL);

INSERT INTO service_items (
  id, establishment_id, name, description, duration_minutes, price, active, sort_order
) VALUES (
  900001, 900001, 'Serviço Canário', 'Dado exclusivamente sintético do rehearsal.', 60, 99.90, TRUE, 1
);

INSERT INTO professionals (
  id, establishment_id, name, bio, whatsapp, active, sort_order
) VALUES (
  900001, 900001, 'Profissional Canário', 'Fixture sintética de recuperação.', '5517900000002', TRUE, 1
);

INSERT INTO professional_services (professional_id, service_item_id)
VALUES (900001, 900001);

INSERT INTO customers (
  id, establishment_id, name, phone_normalized, no_show_count, blocked
) VALUES (
  900001, 900001, 'Cliente Canário', '17900000003', 0, FALSE
);

INSERT INTO appointments (
  id, establishment_id, customer_id, service_item_id, professional_id,
  start_at, end_at, status, client_ip, public_token
) VALUES (
  900001, 900001, 900001, 900001, 900001,
  TIMESTAMP '2099-06-15 10:00:00', TIMESTAMP '2099-06-15 11:00:00',
  'COMPLETED', '192.0.2.10', 'recovery-rehearsal-token-000001'
);

COMMIT;
