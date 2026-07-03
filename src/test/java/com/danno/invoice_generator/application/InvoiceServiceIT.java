package com.danno.invoice_generator.application;

import com.danno.invoice_generator.PostgresIntegrationTest;
import com.danno.invoice_generator.domain.Address;
import com.danno.invoice_generator.domain.Customer;
import com.danno.invoice_generator.domain.Invoice;
import com.danno.invoice_generator.domain.InvoiceStatus;
import com.danno.invoice_generator.domain.LineItem;
import com.danno.invoice_generator.domain.TaxRule;
import com.danno.invoice_generator.domain.exception.CustomerNotFoundException;
import com.danno.invoice_generator.domain.exception.InvalidInvoiceStateException;
import com.danno.invoice_generator.domain.exception.InvoiceNotFoundException;
import com.danno.invoice_generator.domain.exception.InvoiceNotModifiableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class InvoiceServiceIT extends PostgresIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void given_existingCustomer_when_createDraft_then_invoiceIsPersistedInDraftStatus() {
        Customer customer = persistCustomer("ES");

        Invoice invoice = invoiceService.createDraft(customer.getId(), LocalDate.now().plusDays(30));

        assertThat(invoice.getId()).isNotNull();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(invoice.getCustomer().getId()).isEqualTo(customer.getId());
    }

    @Test
    void given_customerWithMatchingTaxRule_when_addLineItem_then_lineItemUsesRegionRate() {
        Customer customer = persistCustomer("AR");
        persistTaxRule("AR", new BigDecimal("0.1050"));
        Invoice invoice = invoiceService.createDraft(customer.getId(), LocalDate.now().plusDays(30));

        LineItem lineItem = invoiceService.addLineItem(
                invoice.getId(), "Consulting", BigDecimal.ONE, new BigDecimal("100.00"));

        assertThat(lineItem.getTaxRate()).isEqualByComparingTo("0.1050");
    }

    @Test
    void given_draftInvoice_when_addLineItem_then_lineItemIsPersistedWithGeneratedId() {
        Customer customer = persistCustomer("ES");
        Invoice invoice = invoiceService.createDraft(customer.getId(), LocalDate.now().plusDays(30));

        LineItem lineItem = invoiceService.addLineItem(
                invoice.getId(), "Consulting", BigDecimal.ONE, new BigDecimal("100.00"));
        // Force the flush a real (non-test-wrapped) transaction commit would trigger, so the
        // cascade-persisted LineItem's generated id is assigned before we assert on it.
        entityManager.flush();

        assertThat(lineItem.getId()).isNotNull();
    }

    @Test
    void given_customerWithNoMatchingTaxRule_when_addLineItem_then_lineItemUsesDefaultRate() {
        Customer customer = persistCustomer("ZZ");
        Invoice invoice = invoiceService.createDraft(customer.getId(), LocalDate.now().plusDays(30));

        LineItem lineItem = invoiceService.addLineItem(
                invoice.getId(), "Consulting", BigDecimal.ONE, new BigDecimal("100.00"));

        assertThat(lineItem.getTaxRate()).isEqualByComparingTo(TaxRule.DEFAULT_RATE);
    }

    @Test
    void given_paidInvoice_when_addLineItem_then_throwsInvoiceNotModifiableException() {
        Customer customer = persistCustomer("ES");
        Invoice invoice = invoiceService.createDraft(customer.getId(), LocalDate.now().plusDays(30));
        invoiceService.addLineItem(invoice.getId(), "Consulting", BigDecimal.ONE, new BigDecimal("100.00"));
        invoiceService.issueInvoice(invoice.getId());
        invoiceService.markAsPaid(invoice.getId());

        assertThatThrownBy(() ->
                invoiceService.addLineItem(invoice.getId(), "Extra", BigDecimal.ONE, BigDecimal.TEN))
                .isInstanceOf(InvoiceNotModifiableException.class);
    }

    @Test
    void given_draftInvoice_when_issueInvoice_then_statusIsIssuedAndNumberFollowsFormat() {
        Customer customer = persistCustomer("ES");
        Invoice invoice = invoiceService.createDraft(customer.getId(), LocalDate.now().plusDays(30));
        invoiceService.addLineItem(invoice.getId(), "Consulting", BigDecimal.ONE, new BigDecimal("100.00"));

        Invoice issued = invoiceService.issueInvoice(invoice.getId());

        assertThat(issued.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(issued.getNumber().value()).matches("^INV-\\d{4}-\\d{6,}$");
        assertThat(issued.getIssueDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void given_twoInvoicesIssuedInSameFiscalYear_when_issued_then_numbersAreSequential() {
        Customer customer = persistCustomer("ES");
        Invoice first = invoiceService.createDraft(customer.getId(), LocalDate.now().plusDays(30));
        Invoice second = invoiceService.createDraft(customer.getId(), LocalDate.now().plusDays(30));

        Invoice issuedFirst = invoiceService.issueInvoice(first.getId());
        Invoice issuedSecond = invoiceService.issueInvoice(second.getId());

        assertThat(sequenceOf(issuedSecond)).isEqualTo(sequenceOf(issuedFirst) + 1);
    }

    @Test
    void given_unknownCustomerId_when_createDraft_then_throwsCustomerNotFoundException() {
        assertThatThrownBy(() -> invoiceService.createDraft(UUID.randomUUID(), LocalDate.now().plusDays(30)))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void given_unknownInvoiceId_when_findById_then_throwsInvoiceNotFoundException() {
        assertThatThrownBy(() -> invoiceService.findById(UUID.randomUUID()))
                .isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void given_draftInvoice_when_markAsPaidWithoutIssuing_then_throwsInvalidInvoiceStateException() {
        Customer customer = persistCustomer("ES");
        Invoice invoice = invoiceService.createDraft(customer.getId(), LocalDate.now().plusDays(30));

        assertThatThrownBy(() -> invoiceService.markAsPaid(invoice.getId()))
                .isInstanceOf(InvalidInvoiceStateException.class);
    }

    @Test
    void given_cancelledInvoice_when_issueInvoice_then_throwsInvalidInvoiceStateException() {
        Customer customer = persistCustomer("ES");
        Invoice invoice = invoiceService.createDraft(customer.getId(), LocalDate.now().plusDays(30));
        invoiceService.cancelInvoice(invoice.getId());

        assertThatThrownBy(() -> invoiceService.issueInvoice(invoice.getId()))
                .isInstanceOf(InvalidInvoiceStateException.class);
    }

    private Customer persistCustomer(String regionCode) {
        Customer customer = new Customer("Acme", "TAX-1", "billing@acme.test", regionCode,
                new Address("Main St 1", "City", "00000", "Country"));
        entityManager.persist(customer);
        return customer;
    }

    private void persistTaxRule(String regionCode, BigDecimal rate) {
        entityManager.persist(new TaxRule(regionCode, rate, regionCode + " rate"));
    }

    private static long sequenceOf(Invoice invoice) {
        String value = invoice.getNumber().value();
        return Long.parseLong(value.substring(value.lastIndexOf('-') + 1));
    }
}
