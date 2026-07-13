package com.danno.invoice_generator.application;

import com.danno.invoice_generator.application.port.BillingSummaryReportGenerator;
import com.danno.invoice_generator.application.port.CustomerRepository;
import com.danno.invoice_generator.application.port.InvoiceRepository;
import com.danno.invoice_generator.domain.InvoiceStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class BillingSummaryService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillingSummaryReportGenerator reportGenerator;

    public BillingSummaryService(CustomerRepository customerRepository,
                                  InvoiceRepository invoiceRepository,
                                  BillingSummaryReportGenerator reportGenerator) {
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.reportGenerator = reportGenerator;
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf() {
        return reportGenerator.generate(buildSummary());
    }

    private BillingSummary buildSummary() {
        long totalCustomers = customerRepository.count();
        Map<InvoiceStatus, Long> counts = invoiceRepository.countGroupedByStatus();

        List<StatusCount> statusBreakdown = Arrays.stream(InvoiceStatus.values())
                .map(status -> new StatusCount(status.name(), counts.getOrDefault(status, 0L)))
                .toList();
        long totalInvoices = statusBreakdown.stream().mapToLong(StatusCount::getCount).sum();

        return new BillingSummary(totalCustomers, totalInvoices, statusBreakdown);
    }
}
