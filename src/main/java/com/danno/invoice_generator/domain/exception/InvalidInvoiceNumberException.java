package com.danno.invoice_generator.domain.exception;

public class InvalidInvoiceNumberException extends DomainException {

    public InvalidInvoiceNumberException(String value) {
        super("Invalid invoice number format: " + value);
    }
}
