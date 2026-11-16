-- Schema de licenciamento ECDSA P-256 para Neon Postgres (MK21 MultiServidor)

CREATE TABLE IF NOT EXISTS orders (
  code TEXT PRIMARY KEY,
  tier TEXT NOT NULL DEFAULT 'pro',
  seats INT NOT NULL DEFAULT 1,
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS licenses (
  device_id TEXT PRIMARY KEY,
  order_code TEXT NOT NULL REFERENCES orders(code),
  tier TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT false,
  last_seen TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_licenses_order_active
  ON licenses (order_code, revoked);
