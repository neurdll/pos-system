CREATE TABLE products (
  id BIGSERIAL PRIMARY KEY,
  sku VARCHAR(50) NOT NULL UNIQUE,
  barcode VARCHAR(64) UNIQUE,
  name VARCHAR(150) NOT NULL,
  category VARCHAR(80),
  price_cents BIGINT NOT NULL CHECK (price_cents >= 0),
  tax_rate_bps INTEGER NOT NULL DEFAULT 1600 CHECK (tax_rate_bps >= 0 AND tax_rate_bps <= 10000),
  stock_qty INTEGER NOT NULL DEFAULT 0,
  track_inventory BOOLEAN NOT NULL DEFAULT TRUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_name_lower ON products (LOWER(name));
