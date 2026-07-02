package com.danno.invoice_generator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tax_rules")
public class TaxRule {

    /**
     * Fallback IVA rate applied when no TaxRule matches a customer's region.
     */
    public static final BigDecimal DEFAULT_RATE = new BigDecimal("0.21");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "region_code", nullable = false, unique = true)
    private String regionCode;

    @Column(name = "rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal rate;

    @Column(name = "description")
    private String description;

    protected TaxRule() {
    }

    public TaxRule(String regionCode, BigDecimal rate, String description) {
        this.regionCode = regionCode;
        this.rate = rate;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TaxRule other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
