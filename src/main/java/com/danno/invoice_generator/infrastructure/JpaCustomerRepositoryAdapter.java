package com.danno.invoice_generator.infrastructure;

import com.danno.invoice_generator.application.port.CustomerRepository;
import com.danno.invoice_generator.domain.Customer;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaCustomerRepositoryAdapter implements CustomerRepository {

    private final CustomerJpaRepository jpaRepository;

    JpaCustomerRepositoryAdapter(CustomerJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return jpaRepository.findById(id);
    }
}
