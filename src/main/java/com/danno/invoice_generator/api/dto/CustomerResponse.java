package com.danno.invoice_generator.api.dto;

import com.danno.invoice_generator.domain.Address;
import com.danno.invoice_generator.domain.Customer;

import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String taxId,
        String email,
        String regionCode,
        String street,
        String city,
        String postalCode,
        String country) {

    public static CustomerResponse from(Customer customer) {
        Address address = customer.getAddress();
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getTaxId(),
                customer.getEmail(),
                customer.getRegionCode(),
                address != null ? address.getStreet() : null,
                address != null ? address.getCity() : null,
                address != null ? address.getPostalCode() : null,
                address != null ? address.getCountry() : null);
    }
}
