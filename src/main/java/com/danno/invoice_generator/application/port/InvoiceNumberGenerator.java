package com.danno.invoice_generator.application.port;

import com.danno.invoice_generator.domain.InvoiceNumber;

/**
 * Atomically allocates the next {@link InvoiceNumber} for a fiscal year.
 * Implementations must participate in the caller's active transaction —
 * calling this outside of one is a programming error.
 */
public interface InvoiceNumberGenerator {

    InvoiceNumber nextNumber(int fiscalYear);
}
