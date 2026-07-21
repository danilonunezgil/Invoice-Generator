package com.danno.invoice_generator.application.port;

import com.danno.invoice_generator.domain.Invoice;
import com.danno.invoice_generator.domain.InvoiceStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {

    Invoice save(Invoice invoice);

    Optional<Invoice> findById(UUID id);

    Map<InvoiceStatus, Long> countGroupedByStatus();

    List<Invoice> findOverdueByCustomer(UUID customerId, LocalDate asOf);
}
