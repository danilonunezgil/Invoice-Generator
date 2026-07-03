package com.danno.invoice_generator.infrastructure;

import com.danno.invoice_generator.domain.TaxRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface TaxRuleJpaRepository extends JpaRepository<TaxRule, UUID> {

    Optional<TaxRule> findByRegionCode(String regionCode);
}
