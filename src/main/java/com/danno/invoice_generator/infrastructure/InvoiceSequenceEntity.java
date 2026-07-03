package com.danno.invoice_generator.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Infrastructure-only bookkeeping row for {@link com.danno.invoice_generator.domain.InvoiceNumber}
 * sequence allocation. Not a domain concept — plain JPA entity backing invoice_sequence.
 */
@Entity
@Table(name = "invoice_sequence")
class InvoiceSequenceEntity {

    @Id
    @Column(name = "fiscal_year")
    private Integer fiscalYear;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    protected InvoiceSequenceEntity() {
    }

    long incrementAndGet() {
        lastSequence++;
        return lastSequence;
    }
}
