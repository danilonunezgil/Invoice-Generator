package com.danno.invoice_generator.application;

import com.danno.invoice_generator.PostgresIntegrationTest;
import com.danno.invoice_generator.domain.TaxRule;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class TaxRuleServiceIT extends PostgresIntegrationTest {

    @Autowired
    private TaxRuleService taxRuleService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void given_regionWithRule_when_resolve_then_returnsConfiguredRate() {
        entityManager.persist(new TaxRule("AR", new BigDecimal("0.1050"), "AR rate"));

        TaxRuleService.ResolvedTaxRate resolved = taxRuleService.resolve("AR");

        assertThat(resolved.rate()).isEqualByComparingTo("0.1050");
        assertThat(resolved.isDefault()).isFalse();
    }

    @Test
    void given_regionWithoutRule_when_resolve_then_returnsDefaultRate() {
        TaxRuleService.ResolvedTaxRate resolved = taxRuleService.resolve("ZZ");

        assertThat(resolved.rate()).isEqualByComparingTo(TaxRule.DEFAULT_RATE);
        assertThat(resolved.isDefault()).isTrue();
    }
}
