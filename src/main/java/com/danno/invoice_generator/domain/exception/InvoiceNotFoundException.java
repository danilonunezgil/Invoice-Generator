package com.danno.invoice_generator.domain.exception;

import java.util.UUID;

public class InvoiceNotFoundException extends DomainException {

    public InvoiceNotFoundException(UUID invoiceId) {
        super("Invoice not found: " + invoiceId);
    }
}
