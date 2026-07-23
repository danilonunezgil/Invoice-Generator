package com.danno.invoice_generator.domain.exception;

public class InvalidPdfUploadException extends DomainException {

    public InvalidPdfUploadException(String message) {
        super(message);
    }
}
