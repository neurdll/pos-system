CREATE SEQUENCE ticket_number_seq START 1;

CREATE TABLE sales (
  id BIGSERIAL PRIMARY KEY,
  ticket_number VARCHAR(32) UNIQUE,
  shift_id BIGINT NOT NULL REFERENCES cash_shifts(id),
  cashier_id BIGINT NOT NULL REFERENCES users(id),
  status VARCHAR(24) NOT NULL CHECK (status IN ('OPEN', 'PAID', 'VOIDED', 'PARTIAL_RETURN', 'RETURNED')),
  subtotal_cents BIGINT NOT NULL DEFAULT 0 CHECK (subtotal_cents >= 0),
  tax_cents BIGINT NOT NULL DEFAULT 0 CHECK (tax_cents >= 0),
  discount_cents BIGINT NOT NULL DEFAULT 0 CHECK (discount_cents >= 0),
  total_cents BIGINT NOT NULL DEFAULT 0 CHECK (total_cents >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_sales_single_open_per_shift ON sales (shift_id) WHERE status = 'OPEN';

CREATE TABLE sale_items (
  id BIGSERIAL PRIMARY KEY,
  sale_id BIGINT NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
  product_id BIGINT NOT NULL REFERENCES products(id),
  product_name_snapshot VARCHAR(150) NOT NULL,
  sku_snapshot VARCHAR(50) NOT NULL,
  qty INTEGER NOT NULL CHECK (qty > 0),
  unit_price_cents BIGINT NOT NULL CHECK (unit_price_cents >= 0),
  tax_rate_bps INTEGER NOT NULL CHECK (tax_rate_bps >= 0),
  line_subtotal_cents BIGINT NOT NULL CHECK (line_subtotal_cents >= 0),
  line_tax_cents BIGINT NOT NULL CHECK (line_tax_cents >= 0),
  line_total_cents BIGINT NOT NULL CHECK (line_total_cents >= 0)
);
