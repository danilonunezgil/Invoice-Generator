package com.danno.invoice_generator.domain;

import com.danno.invoice_generator.domain.exception.InvalidInvoiceStateException;
import com.danno.invoice_generator.domain.exception.InvoiceNotModifiableException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Embedded
    private InvoiceNumber number;

    @Column(name = "fiscal_year")
    private Integer fiscalYear;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LineItem> lineItems = new ArrayList<>();

    @Version
    @Column(name = "version")
    private Long version;

    protected Invoice() {
    }

    public Invoice(Customer customer, LocalDate dueDate) {
        this.customer = customer;
        this.dueDate = dueDate;
        this.status = InvoiceStatus.DRAFT;
    }

    public LineItem addLineItem(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate) {
        ensureModifiable();
        LineItem lineItem = new LineItem(this, description, quantity, unitPrice, taxRate);
        lineItems.add(lineItem);
        return lineItem;
    }

    public void removeLineItem(UUID lineItemId) {
        ensureModifiable();
        lineItems.removeIf(item -> item.getId() != null && item.getId().equals(lineItemId));
    }

    public void issue(InvoiceNumber number) {
        if (status != InvoiceStatus.DRAFT) {
            throw new InvalidInvoiceStateException(id, status, "issue");
        }
        this.number = number;
        this.fiscalYear = number.fiscalYear();
        this.issueDate = LocalDate.now();
        this.status = InvoiceStatus.ISSUED;
    }

    public void markAsPaid() {
        if (status != InvoiceStatus.ISSUED) {
            throw new InvalidInvoiceStateException(id, status, "markAsPaid");
        }
        this.status = InvoiceStatus.PAID;
    }

    public void cancel() {
        if (status != InvoiceStatus.DRAFT && status != InvoiceStatus.ISSUED) {
            throw new InvalidInvoiceStateException(id, status, "cancel");
        }
        this.status = InvoiceStatus.CANCELLED;
    }

    private void ensureModifiable() {
        if (status == InvoiceStatus.PAID || status == InvoiceStatus.CANCELLED) {
            throw new InvoiceNotModifiableException(id, status);
        }
    }

    @Transient
    public BigDecimal getSubtotal() {
        return lineItems.stream()
                .map(LineItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transient
    public BigDecimal getTaxTotal() {
        return lineItems.stream()
                .map(LineItem::getTaxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transient
    public BigDecimal getTotal() {
        return getSubtotal().add(getTaxTotal());
    }

    public UUID getId() {
        return id;
    }

    public InvoiceNumber getNumber() {
        return number;
    }

    public Integer getFiscalYear() {
        return fiscalYear;
    }

    public Customer getCustomer() {
        return customer;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public List<LineItem> getLineItems() {
        return Collections.unmodifiableList(lineItems);
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Invoice other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
