package com.danno.invoice_generator.domain.exception;

import com.danno.invoice_generator.domain.InvoiceStatus;

import java.util.UUID;

public class InvalidInvoiceStateException extends DomainException {

    public InvalidInvoiceStateException(UUID invoiceId, InvoiceStatus current, String attemptedTransition) {
        super("Invoice " + invoiceId + " in status " + current + " cannot transition via " + attemptedTransition);
    }
}
