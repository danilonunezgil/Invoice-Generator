package com.danno.invoice_generator.api.dto;

import com.danno.invoice_generator.domain.LineItem;

import java.math.BigDecimal;
import java.util.UUID;

public record LineItemResponse(
        UUID id,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal total) {

    public static LineItemResponse from(LineItem lineItem) {
        return new LineItemResponse(
                lineItem.getId(),
                lineItem.getDescription(),
                lineItem.getQuantity(),
                lineItem.getUnitPrice(),
                lineItem.getTaxRate(),
                lineItem.getSubtotal(),
                lineItem.getTaxAmount(),
                lineItem.getTotal());
    }
}
