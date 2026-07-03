package com.danno.invoice_generator.application.port;

import com.danno.invoice_generator.domain.TaxRule;

import java.util.Optional;

public interface TaxRuleRepository {

    Optional<TaxRule> findByRegionCode(String regionCode);
}
