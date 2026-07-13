package com.danno.invoice_generator.application.port;

import com.danno.invoice_generator.application.BillingSummary;

public interface BillingSummaryReportGenerator {

    byte[] generate(BillingSummary summary);
}
