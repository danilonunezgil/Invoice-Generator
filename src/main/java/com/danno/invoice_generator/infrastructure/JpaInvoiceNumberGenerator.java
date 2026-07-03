package com.danno.invoice_generator.infrastructure;

import com.danno.invoice_generator.application.port.InvoiceNumberGenerator;
import com.danno.invoice_generator.domain.InvoiceNumber;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaInvoiceNumberGenerator implements InvoiceNumberGenerator {

    private final InvoiceSequenceJpaRepository sequenceRepository;

    JpaInvoiceNumberGenerator(InvoiceSequenceJpaRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public InvoiceNumber nextNumber(int fiscalYear) {
        sequenceRepository.ensureRowExists(fiscalYear);
        InvoiceSequenceEntity sequence = sequenceRepository.findForUpdate(fiscalYear)
                .orElseThrow(() -> new IllegalStateException(
                        "invoice_sequence row missing for fiscal year " + fiscalYear));
        long nextSequence = sequence.incrementAndGet();
        return InvoiceNumber.of(fiscalYear, nextSequence);
    }
}
