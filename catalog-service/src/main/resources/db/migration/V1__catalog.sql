CREATE TABLE products (
    sku varchar(64) PRIMARY KEY,
    name varchar(200) NOT NULL,
    price numeric(12,2) NOT NULL CHECK (price > 0),
    currency varchar(3) NOT NULL CHECK (currency = 'USD')
);
INSERT INTO products (sku, name, price, currency) VALUES
('KEYBOARD-001', 'Mechanical keyboard', 89.00, 'USD'),
('MONITOR-001', '27-inch monitor', 299.00, 'USD');
