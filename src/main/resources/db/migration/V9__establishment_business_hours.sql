CREATE TABLE establishment_business_hours (
  id BIGSERIAL PRIMARY KEY,
  establishment_id BIGINT NOT NULL REFERENCES establishments(id),
  day_of_week VARCHAR(9) NOT NULL,
  is_open BOOLEAN NOT NULL,
  opening_time TIME,
  closing_time TIME,
  CONSTRAINT uk_business_hours_est_day UNIQUE(establishment_id, day_of_week),
  CONSTRAINT ck_business_hours_day CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
  CONSTRAINT ck_business_hours_state CHECK (
    (is_open = TRUE AND opening_time IS NOT NULL AND closing_time IS NOT NULL AND opening_time < closing_time)
    OR
    (is_open = FALSE AND opening_time IS NULL AND closing_time IS NULL)
  )
);

INSERT INTO establishment_business_hours(establishment_id, day_of_week, is_open, opening_time, closing_time)
SELECT e.id, d.day_of_week, TRUE, TIME '08:00', TIME '18:00'
FROM establishments e
CROSS JOIN (VALUES
  ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'),
  ('FRIDAY'), ('SATURDAY'), ('SUNDAY')
) AS d(day_of_week);
