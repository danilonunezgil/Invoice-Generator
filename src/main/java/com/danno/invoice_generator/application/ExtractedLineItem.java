package com.danno.invoice_generator.application;

import java.math.BigDecimal;

public record ExtractedLineItem(
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        ExtractedLineItemCategory category,
        String categoryDetail) {
}
