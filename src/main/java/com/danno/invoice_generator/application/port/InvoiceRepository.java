package com.danno.invoice_generator.application.port;

import com.danno.invoice_generator.domain.Invoice;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {

    Invoice save(Invoice invoice);

    Optional<Invoice> findById(UUID id);
}
