package com.danno.invoice_generator.infrastructure;

import com.danno.invoice_generator.domain.Invoice;
import com.danno.invoice_generator.domain.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface InvoiceJpaRepository extends JpaRepository<Invoice, UUID> {

    @Query("SELECT i.status AS status, COUNT(i) AS total FROM Invoice i GROUP BY i.status")
    List<StatusCountProjection> countGroupedByStatus();

    interface StatusCountProjection {
        InvoiceStatus getStatus();

        long getTotal();
    }
}
