package com.danno.invoice_generator.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExtractedInvoiceData(
        String vendorName,
        LocalDate invoiceDate,
        List<ExtractedLineItem> lineItems,
        BigDecimal total,
        boolean totalsReconciled,
        int retriesUsed) {
}
