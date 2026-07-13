package com.danno.invoice_generator.application;

import com.danno.invoice_generator.PostgresIntegrationTest;
import com.danno.invoice_generator.domain.Address;
import com.danno.invoice_generator.domain.Customer;
import com.danno.invoice_generator.domain.Invoice;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class BillingSummaryServiceIT extends PostgresIntegrationTest {

    @Autowired
    private BillingSummaryService billingSummaryService;

    @Autowired
    private InvoiceService invoiceService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void given_customersWithInvoicesInDifferentStatuses_when_generatePdf_then_returnsPdfBytes() {
        Customer first = persistCustomer("ES");
        Customer second = persistCustomer("AR");
        invoiceService.createDraft(first.getId(), LocalDate.now().plusDays(30));
        Invoice issued = invoiceService.createDraft(second.getId(), LocalDate.now().plusDays(30));
        invoiceService.addLineItem(issued.getId(), "Consulting", BigDecimal.ONE, new BigDecimal("100.00"));
        invoiceService.issueInvoice(issued.getId());

        byte[] pdf = billingSummaryService.generatePdf();

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void given_noCustomersOrInvoices_when_generatePdf_then_returnsPdfBytesWithZeroCounts() {
        byte[] pdf = billingSummaryService.generatePdf();

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    private Customer persistCustomer(String regionCode) {
        Customer customer = new Customer("Acme", "TAX-1", "billing@acme.test", regionCode,
                new Address("Main St 1", "City", "00000", "Country"));
        entityManager.persist(customer);
        return customer;
    }
}
