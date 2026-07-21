package com.danno.invoice_generator.infrastructure;

import com.danno.invoice_generator.application.port.InvoiceRepository;
import com.danno.invoice_generator.domain.Invoice;
import com.danno.invoice_generator.domain.InvoiceStatus;
import com.danno.invoice_generator.domain.exception.DuplicateInvoiceNumberException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Override
    public Map<InvoiceStatus, Long> countGroupedByStatus() {
        return jpaRepository.countGroupedByStatus().stream()
                .collect(Collectors.toMap(
                        InvoiceJpaRepository.StatusCountProjection::getStatus,
                        InvoiceJpaRepository.StatusCountProjection::getTotal));
    }

    @Override
    public List<Invoice> findOverdueByCustomer(UUID customerId, LocalDate asOf) {
        return jpaRepository.findByCustomer_IdAndStatusAndDueDateBefore(customerId, InvoiceStatus.ISSUED, asOf);
    }
}
