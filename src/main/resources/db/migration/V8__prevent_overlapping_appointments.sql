DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM appointments
    WHERE start_at >= end_at
  ) THEN
    RAISE EXCEPTION 'Cannot install appointment overlap protection: invalid appointment periods exist.'
      USING HINT = 'Fix appointments where start_at is greater than or equal to end_at, then retry the migration.';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM appointments a
    JOIN appointments b
      ON a.id < b.id
     AND a.establishment_id = b.establishment_id
     AND a.professional_id = b.professional_id
     AND a.start_at < b.end_at
     AND a.end_at > b.start_at
    WHERE a.status IN ('CONFIRMED', 'PENDING_APPROVAL')
      AND b.status IN ('CONFIRMED', 'PENDING_APPROVAL')
  ) THEN
    RAISE EXCEPTION 'Cannot install appointment overlap protection: overlapping blocking appointments exist.'
      USING HINT = 'Resolve the reported scheduling conflicts without deleting history, then retry the migration.';
  END IF;
END
$$;

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointments
  ADD CONSTRAINT ck_appointments_valid_period
  CHECK (start_at < end_at);

ALTER TABLE appointments
  ADD CONSTRAINT ex_appointments_no_blocking_overlap
  EXCLUDE USING gist (
    establishment_id WITH =,
    professional_id WITH =,
    tsrange(start_at, end_at, '[)') WITH &&
  )
  WHERE (status IN ('CONFIRMED', 'PENDING_APPROVAL'));
