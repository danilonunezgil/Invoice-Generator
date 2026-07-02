CREATE TABLE tax_rules (
    id UUID PRIMARY KEY,
    region_code VARCHAR(10) NOT NULL UNIQUE,
    rate NUMERIC(5, 4) NOT NULL,
    description VARCHAR(255)
);
