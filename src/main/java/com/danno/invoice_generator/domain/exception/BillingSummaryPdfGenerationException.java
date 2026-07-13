package com.danno.invoice_generator.domain.exception;

public class BillingSummaryPdfGenerationException extends DomainException {

    public BillingSummaryPdfGenerationException(Throwable cause) {
        super("No se pudo generar el PDF del resumen de facturación");
        initCause(cause);
    }
}
