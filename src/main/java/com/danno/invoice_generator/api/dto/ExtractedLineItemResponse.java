package com.danno.invoice_generator.api.dto;

import com.danno.invoice_generator.application.ExtractedLineItem;

import java.math.BigDecimal;

public record ExtractedLineItemResponse(
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        String category,
        String categoryDetail) {

    public static ExtractedLineItemResponse from(ExtractedLineItem item) {
        return new ExtractedLineItemResponse(
                item.description(),
                item.quantity(),
                item.unitPrice(),
                item.category().name().toLowerCase(),
                item.categoryDetail());
    }
}
