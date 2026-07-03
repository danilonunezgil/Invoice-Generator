package com.danno.invoice_generator.api.dto;

import com.danno.invoice_generator.domain.Invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        String number,
        Integer fiscalYear,
        UUID customerId,
        String status,
        LocalDate issueDate,
        LocalDate dueDate,
        List<LineItemResponse> lineItems,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal total) {

    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getNumber() != null ? invoice.getNumber().value() : null,
                invoice.getFiscalYear(),
                invoice.getCustomer().getId(),
                invoice.getStatus().name(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getLineItems().stream().map(LineItemResponse::from).toList(),
                invoice.getSubtotal(),
                invoice.getTaxTotal(),
                invoice.getTotal());
    }
}
