package com.danno.invoice_generator.application;

import com.danno.invoice_generator.application.port.TaxRuleRepository;
import com.danno.invoice_generator.domain.TaxRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TaxRuleService {

    private final TaxRuleRepository taxRuleRepository;

    public TaxRuleService(TaxRuleRepository taxRuleRepository) {
        this.taxRuleRepository = taxRuleRepository;
    }

    @Transactional(readOnly = true)
    public ResolvedTaxRate resolve(String regionCode) {
        return taxRuleRepository.findByRegionCode(regionCode)
                .map(rule -> new ResolvedTaxRate(regionCode, rule.getRate(), false))
                .orElseGet(() -> new ResolvedTaxRate(regionCode, TaxRule.DEFAULT_RATE, true));
    }

    public record ResolvedTaxRate(String regionCode, BigDecimal rate, boolean isDefault) {
    }
}
