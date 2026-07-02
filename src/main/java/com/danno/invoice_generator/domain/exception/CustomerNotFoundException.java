package com.danno.invoice_generator.domain.exception;

import java.util.UUID;

public class CustomerNotFoundException extends DomainException {

    public CustomerNotFoundException(UUID customerId) {
        super("Customer not found: " + customerId);
    }
}
