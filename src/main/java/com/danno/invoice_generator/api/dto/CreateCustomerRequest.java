package com.danno.invoice_generator.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateCustomerRequest(
        @NotBlank String name,
        @NotBlank String taxId,
        @NotBlank @Email String email,
        @NotBlank String regionCode,
        String street,
        String city,
        String postalCode,
        String country) {
}
