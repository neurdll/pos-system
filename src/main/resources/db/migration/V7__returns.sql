CREATE TABLE returns (
  id BIGSERIAL PRIMARY KEY,
  sale_id BIGINT NOT NULL REFERENCES sales(id),
  shift_id BIGINT NOT NULL REFERENCES cash_shifts(id),
  processed_by BIGINT NOT NULL REFERENCES users(id),
  refund_method VARCHAR(20) NOT NULL CHECK (refund_method IN ('CASH', 'CARD', 'TRANSFER')),
  total_refund_cents BIGINT NOT NULL CHECK (total_refund_cents > 0),
  reason VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE return_items (
  id BIGSERIAL PRIMARY KEY,
  return_id BIGINT NOT NULL REFERENCES returns(id) ON DELETE CASCADE,
  sale_item_id BIGINT NOT NULL REFERENCES sale_items(id),
  product_id BIGINT NOT NULL REFERENCES products(id),
  qty INTEGER NOT NULL CHECK (qty > 0),
  refund_amount_cents BIGINT NOT NULL CHECK (refund_amount_cents >= 0),
  restock BOOLEAN NOT NULL DEFAULT TRUE
);
