package com.danno.invoice_generator.infrastructure;

import com.danno.invoice_generator.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface CustomerJpaRepository extends JpaRepository<Customer, UUID> {
}
