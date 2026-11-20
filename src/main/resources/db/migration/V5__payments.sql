CREATE TABLE payments (
  id BIGSERIAL PRIMARY KEY,
  sale_id BIGINT NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
  method VARCHAR(20) NOT NULL CHECK (method IN ('CASH', 'CARD', 'TRANSFER')),
  amount_cents BIGINT NOT NULL CHECK (amount_cents >= 0),
  tendered_cents BIGINT NOT NULL CHECK (tendered_cents >= 0),
  change_cents BIGINT NOT NULL DEFAULT 0 CHECK (change_cents >= 0),
  reference VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_sale_id ON payments (sale_id);
