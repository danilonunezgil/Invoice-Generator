package com.danno.invoice_generator.domain.exception;

public class InvoicePdfExtractionException extends DomainException {

    public InvoicePdfExtractionException(String message) {
        super(message);
    }

    public InvoicePdfExtractionException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
