package com.danno.invoice_generator.api;

import com.danno.invoice_generator.api.dto.TaxRuleResponse;
import com.danno.invoice_generator.application.TaxRuleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tax-rules")
public class TaxRuleController {

    private final TaxRuleService taxRuleService;

    public TaxRuleController(TaxRuleService taxRuleService) {
        this.taxRuleService = taxRuleService;
    }

    @GetMapping("/{regionCode}")
    public TaxRuleResponse resolve(@PathVariable String regionCode) {
        return TaxRuleResponse.from(taxRuleService.resolve(regionCode));
    }
}
