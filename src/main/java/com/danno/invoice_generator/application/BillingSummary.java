package com.danno.invoice_generator.application;

import java.util.List;

public class BillingSummary {

    private final long totalCustomers;
    private final long totalInvoices;
    private final List<StatusCount> statusBreakdown;

    public BillingSummary(long totalCustomers, long totalInvoices, List<StatusCount> statusBreakdown) {
        this.totalCustomers = totalCustomers;
        this.totalInvoices = totalInvoices;
        this.statusBreakdown = statusBreakdown;
    }

    public long getTotalCustomers() {
        return totalCustomers;
    }

    public long getTotalInvoices() {
        return totalInvoices;
    }

    public List<StatusCount> getStatusBreakdown() {
        return statusBreakdown;
    }
}
