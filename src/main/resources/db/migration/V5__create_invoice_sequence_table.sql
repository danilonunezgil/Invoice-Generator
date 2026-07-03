CREATE TABLE invoice_sequence (
    fiscal_year   INTEGER PRIMARY KEY,
    last_sequence BIGINT NOT NULL DEFAULT 0
);
