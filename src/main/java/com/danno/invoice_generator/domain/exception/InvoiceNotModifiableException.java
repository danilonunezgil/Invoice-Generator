package com.danno.invoice_generator.domain.exception;

import com.danno.invoice_generator.domain.InvoiceStatus;

import java.util.UUID;

public class InvoiceNotModifiableException extends DomainException {

    public InvoiceNotModifiableException(UUID invoiceId, InvoiceStatus status) {
        super("Invoice " + invoiceId + " cannot be modified in status " + status);
    }
}
