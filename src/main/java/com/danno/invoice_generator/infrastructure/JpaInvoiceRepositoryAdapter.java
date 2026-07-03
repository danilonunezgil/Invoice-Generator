package com.danno.invoice_generator.infrastructure;

import com.danno.invoice_generator.application.port.InvoiceRepository;
import com.danno.invoice_generator.domain.Invoice;
import com.danno.invoice_generator.domain.exception.DuplicateInvoiceNumberException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaInvoiceRepositoryAdapter implements InvoiceRepository {

    private final InvoiceJpaRepository jpaRepository;

    JpaInvoiceRepositoryAdapter(InvoiceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Invoice save(Invoice invoice) {
        try {
            return jpaRepository.save(invoice);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateInvoiceNumberException(
                    invoice.getNumber() != null ? invoice.getNumber().value() : null);
        }
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        return jpaRepository.findById(id);
    }
}
