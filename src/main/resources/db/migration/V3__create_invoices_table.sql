CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    number VARCHAR(20) UNIQUE,
    fiscal_year INTEGER,
    customer_id UUID NOT NULL REFERENCES customers (id),
    status VARCHAR(20) NOT NULL,
    issue_date DATE,
    due_date DATE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_invoices_customer_id ON invoices (customer_id);
