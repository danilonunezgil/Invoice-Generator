package com.danno.invoice_generator.application.port;

import com.danno.invoice_generator.application.ExtractedInvoiceData;

public interface InvoicePdfExtractor {

    ExtractedInvoiceData extract(byte[] pdfBytes);
}
