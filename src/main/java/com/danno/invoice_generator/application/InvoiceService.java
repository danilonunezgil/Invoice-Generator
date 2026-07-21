package com.danno.invoice_generator.application;

import com.danno.invoice_generator.application.port.CustomerRepository;
import com.danno.invoice_generator.application.port.InvoiceNumberGenerator;
import com.danno.invoice_generator.application.port.InvoicePdfGenerator;
import com.danno.invoice_generator.application.port.InvoiceRepository;
import com.danno.invoice_generator.application.port.TaxRuleRepository;
import com.danno.invoice_generator.domain.Customer;
import com.danno.invoice_generator.domain.Invoice;
import com.danno.invoice_generator.domain.InvoiceNumber;
import com.danno.invoice_generator.domain.LineItem;
import com.danno.invoice_generator.domain.TaxRule;
import com.danno.invoice_generator.domain.exception.CustomerNotFoundException;
import com.danno.invoice_generator.domain.exception.InvalidInvoiceStateException;
import com.danno.invoice_generator.domain.exception.InvoiceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final TaxRuleRepository taxRuleRepository;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final InvoicePdfGenerator invoicePdfGenerator;

    public InvoiceService(InvoiceRepository invoiceRepository,
                           CustomerRepository customerRepository,
                           TaxRuleRepository taxRuleRepository,
                           InvoiceNumberGenerator invoiceNumberGenerator,
                           InvoicePdfGenerator invoicePdfGenerator) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.taxRuleRepository = taxRuleRepository;
        this.invoiceNumberGenerator = invoiceNumberGenerator;
        this.invoicePdfGenerator = invoicePdfGenerator;
    }

    @Transactional
    public Invoice createDraft(UUID customerId, LocalDate dueDate) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        Invoice invoice = new Invoice(customer, dueDate);
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public LineItem addLineItem(UUID invoiceId, String description, BigDecimal quantity, BigDecimal unitPrice) {
        Invoice invoice = getInvoiceOrThrow(invoiceId);
        BigDecimal taxRate = resolveTaxRate(invoice.getCustomer());
        return invoice.addLineItem(description, quantity, unitPrice, taxRate);
    }

    @Transactional
    public Invoice issueInvoice(UUID invoiceId) {
        Invoice invoice = getInvoiceOrThrow(invoiceId);
        int fiscalYear = LocalDate.now().getYear();
        InvoiceNumber number = invoiceNumberGenerator.nextNumber(fiscalYear);
        invoice.issue(number);
        return invoice;
    }

    @Transactional
    public Invoice markAsPaid(UUID invoiceId) {
        Invoice invoice = getInvoiceOrThrow(invoiceId);
        invoice.markAsPaid();
        return invoice;
    }

    @Transactional
    public Invoice cancelInvoice(UUID invoiceId) {
        Invoice invoice = getInvoiceOrThrow(invoiceId);
        invoice.cancel();
        return invoice;
    }

    @Transactional(readOnly = true)
    public Invoice findById(UUID invoiceId) {
        return getInvoiceOrThrow(invoiceId);
    }

    @Transactional(readOnly = true)
    public List<Invoice> findOverdueForCustomer(UUID customerId) {
        return invoiceRepository.findOverdueByCustomer(customerId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID invoiceId) {
        Invoice invoice = getInvoiceOrThrow(invoiceId);
        if (invoice.getNumber() == null) {
            throw new InvalidInvoiceStateException(invoiceId, invoice.getStatus(), "generatePdf");
        }
        return invoicePdfGenerator.generate(invoice);
    }

    private Invoice getInvoiceOrThrow(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        // Force the lazy lineItems collection to load while the transaction (and Hibernate
        // session) is still open, since callers outside application/ (e.g. api/'s DTO mapping)
        // run after this transaction has committed and open-in-view is disabled.
        invoice.getLineItems().size();
        return invoice;
    }

    private BigDecimal resolveTaxRate(Customer customer) {
        return taxRuleRepository.findByRegionCode(customer.getRegionCode())
                .map(TaxRule::getRate)
                .orElse(TaxRule.DEFAULT_RATE);
    }
}
