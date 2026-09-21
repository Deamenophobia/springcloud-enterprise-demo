CREATE TABLE orders (
    id uuid PRIMARY KEY,
    customer_id varchar(255) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    sku varchar(64) NOT NULL,
    quantity integer NOT NULL CHECK (quantity BETWEEN 1 AND 100),
    unit_price numeric(12,2) NOT NULL CHECK (unit_price > 0),
    total numeric(14,2) NOT NULL CHECK (total = unit_price * quantity),
    currency varchar(3) NOT NULL CHECK (currency = 'USD'),
    created_at timestamptz NOT NULL,
    CONSTRAINT orders_customer_idempotency UNIQUE(customer_id, idempotency_key)
);
CREATE INDEX orders_customer_created ON orders(customer_id, created_at DESC);
