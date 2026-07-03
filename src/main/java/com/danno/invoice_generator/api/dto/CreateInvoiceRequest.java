package com.danno.invoice_generator.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateInvoiceRequest(
        @NotNull UUID customerId,
        @NotNull LocalDate dueDate) {
}
