CREATE TABLE stock_movements (
  id BIGSERIAL PRIMARY KEY,
  product_id BIGINT NOT NULL REFERENCES products(id),
  type VARCHAR(20) NOT NULL CHECK (type IN ('SALE', 'RETURN', 'ADJUSTMENT', 'PURCHASE')),
  qty_delta INTEGER NOT NULL CHECK (qty_delta <> 0),
  reference_id BIGINT,
  note VARCHAR(200),
  created_by BIGINT NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stock_movements_product_created ON stock_movements (product_id, created_at DESC);
