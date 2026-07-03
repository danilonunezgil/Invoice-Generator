package com.danno.invoice_generator.infrastructure;

import com.danno.invoice_generator.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface InvoiceJpaRepository extends JpaRepository<Invoice, UUID> {
}
