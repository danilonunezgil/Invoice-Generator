package com.danno.invoice_generator.domain.exception;

import java.util.UUID;

public class InvoicePdfGenerationException extends DomainException {

    public InvoicePdfGenerationException(UUID invoiceId, Throwable cause) {
        super("No se pudo generar el PDF de la factura " + invoiceId);
        initCause(cause);
    }
}
