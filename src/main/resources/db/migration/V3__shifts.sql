CREATE TABLE cash_shifts (
  id BIGSERIAL PRIMARY KEY,
  opened_by BIGINT NOT NULL REFERENCES users(id),
  opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  opening_float_cents BIGINT NOT NULL CHECK (opening_float_cents >= 0),
  closed_by BIGINT REFERENCES users(id),
  closed_at TIMESTAMPTZ,
  counted_cash_cents BIGINT CHECK (counted_cash_cents >= 0),
  expected_cash_cents BIGINT,
  variance_cents BIGINT,
  notes VARCHAR(255),
  status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE UNIQUE INDEX uq_cash_shifts_single_open ON cash_shifts ((1)) WHERE status = 'OPEN';

CREATE TABLE cash_movements (
  id BIGSERIAL PRIMARY KEY,
  shift_id BIGINT NOT NULL REFERENCES cash_shifts(id),
  type VARCHAR(10) NOT NULL CHECK (type IN ('IN', 'OUT')),
  amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
  reason VARCHAR(200) NOT NULL,
  created_by BIGINT NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
