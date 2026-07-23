package com.danno.invoice_generator.api.dto;

import com.danno.invoice_generator.application.ExtractedInvoiceData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExtractedInvoiceResponse(
        String vendorName,
        LocalDate invoiceDate,
        List<ExtractedLineItemResponse> lineItems,
        BigDecimal total,
        boolean totalsReconciled,
        int retriesUsed) {

    public static ExtractedInvoiceResponse from(ExtractedInvoiceData data) {
        return new ExtractedInvoiceResponse(
                data.vendorName(),
                data.invoiceDate(),
                data.lineItems().stream().map(ExtractedLineItemResponse::from).toList(),
                data.total(),
                data.totalsReconciled(),
                data.retriesUsed());
    }
}
