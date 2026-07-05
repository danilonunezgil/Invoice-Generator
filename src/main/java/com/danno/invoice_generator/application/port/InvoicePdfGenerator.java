package com.danno.invoice_generator.application.port;

import com.danno.invoice_generator.domain.Invoice;

public interface InvoicePdfGenerator {

    byte[] generate(Invoice invoice);
}
