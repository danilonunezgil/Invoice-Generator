CREATE TABLE customers (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    tax_id VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    region_code VARCHAR(10) NOT NULL,
    street VARCHAR(255),
    city VARCHAR(255),
    postal_code VARCHAR(20),
    country VARCHAR(255)
);
