package com.danno.invoice_generator.infrastructure;

import com.danno.invoice_generator.application.port.TaxRuleRepository;
import com.danno.invoice_generator.domain.TaxRule;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JpaTaxRuleRepositoryAdapter implements TaxRuleRepository {

    private final TaxRuleJpaRepository jpaRepository;

    JpaTaxRuleRepositoryAdapter(TaxRuleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<TaxRule> findByRegionCode(String regionCode) {
        return jpaRepository.findByRegionCode(regionCode);
    }
}
