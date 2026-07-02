package com.danno.invoice_generator.domain.exception;

public class DuplicateInvoiceNumberException extends DomainException {

    public DuplicateInvoiceNumberException(String invoiceNumber) {
        super("Invoice number already exists: " + invoiceNumber);
    }
}
