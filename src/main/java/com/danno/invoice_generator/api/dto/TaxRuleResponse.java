package com.danno.invoice_generator.api.dto;

import com.danno.invoice_generator.application.TaxRuleService;

import java.math.BigDecimal;

public record TaxRuleResponse(String regionCode, BigDecimal rate, boolean isDefault) {

    public static TaxRuleResponse from(TaxRuleService.ResolvedTaxRate resolved) {
        return new TaxRuleResponse(resolved.regionCode(), resolved.rate(), resolved.isDefault());
    }
}
