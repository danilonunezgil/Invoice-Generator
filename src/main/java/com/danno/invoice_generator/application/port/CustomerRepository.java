package com.danno.invoice_generator.application.port;

import com.danno.invoice_generator.domain.Customer;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

    Optional<Customer> findById(UUID id);

    long count();
}
