package com.danno.invoice_generator.domain;

import com.danno.invoice_generator.domain.exception.InvalidInvoiceNumberException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public class InvoiceNumber {

    private static final Pattern FORMAT = Pattern.compile("^INV-(\\d{4})-(\\d{6,})$");

    @Column(name = "number", unique = true)
    private String value;

    protected InvoiceNumber() {
    }

    private InvoiceNumber(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new InvalidInvoiceNumberException(value);
        }
        this.value = value;
    }

    public int fiscalYear() {
        var matcher = FORMAT.matcher(value);
        matcher.matches();
        return Integer.parseInt(matcher.group(1));
    }

    public static InvoiceNumber of(int fiscalYear, long sequence) {
        return new InvoiceNumber(String.format("INV-%d-%06d", fiscalYear, sequence));
    }

    public static InvoiceNumber parse(String value) {
        return new InvoiceNumber(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvoiceNumber other)) {
            return false;
        }
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
