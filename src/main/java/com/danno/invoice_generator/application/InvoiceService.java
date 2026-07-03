package com.danno.invoice_generator.application;

import com.danno.invoice_generator.application.port.CustomerRepository;
import com.danno.invoice_generator.application.port.InvoiceNumberGenerator;
import com.danno.invoice_generator.application.port.InvoiceRepository;
import com.danno.invoice_generator.application.port.TaxRuleRepository;
import com.danno.invoice_generator.domain.Customer;
import com.danno.invoice_generator.domain.Invoice;
import com.danno.invoice_generator.domain.InvoiceNumber;
import com.danno.invoice_generator.domain.LineItem;
import com.danno.invoice_generator.domain.TaxRule;
import com.danno.invoice_generator.domain.exception.CustomerNotFoundException;
import com.danno.invoice_generator.domain.exception.InvoiceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final TaxRuleRepository taxRuleRepository;
    private final InvoiceNumberGenerator invoiceNumberGenerator;

    public InvoiceService(InvoiceRepository invoiceRepository,
                           CustomerRepository customerRepository,
                           TaxRuleRepository taxRuleRepository,
                           InvoiceNumberGenerator invoiceNumberGenerator) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.taxRuleRepository = taxRuleRepository;
        this.invoiceNumberGenerator = invoiceNumberGenerator;
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
        LineItem lineItem = invoice.addLineItem(description, quantity, unitPrice, taxRate);
        invoiceRepository.save(invoice);
        return lineItem;
    }

    @Transactional
    public Invoice issueInvoice(UUID invoiceId) {
        Invoice invoice = getInvoiceOrThrow(invoiceId);
        int fiscalYear = LocalDate.now().getYear();
        InvoiceNumber number = invoiceNumberGenerator.nextNumber(fiscalYear);
        invoice.issue(number);
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice markAsPaid(UUID invoiceId) {
        Invoice invoice = getInvoiceOrThrow(invoiceId);
        invoice.markAsPaid();
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice cancelInvoice(UUID invoiceId) {
        Invoice invoice = getInvoiceOrThrow(invoiceId);
        invoice.cancel();
        return invoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public Invoice findById(UUID invoiceId) {
        return getInvoiceOrThrow(invoiceId);
    }

    private Invoice getInvoiceOrThrow(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
    }

    private BigDecimal resolveTaxRate(Customer customer) {
        return taxRuleRepository.findByRegionCode(customer.getRegionCode())
                .map(TaxRule::getRate)
                .orElse(TaxRule.DEFAULT_RATE);
    }
}
