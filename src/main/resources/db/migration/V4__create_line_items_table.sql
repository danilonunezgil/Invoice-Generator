CREATE TABLE line_items (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices (id),
    description VARCHAR(255) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL,
    unit_price NUMERIC(19, 4) NOT NULL,
    tax_rate NUMERIC(5, 4) NOT NULL
);

CREATE INDEX idx_line_items_invoice_id ON line_items (invoice_id);
