package com.danno.invoice_generator.application;

import com.danno.invoice_generator.application.port.CustomerRepository;
import com.danno.invoice_generator.domain.Address;
import com.danno.invoice_generator.domain.Customer;
import com.danno.invoice_generator.domain.exception.CustomerNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer createCustomer(String name, String taxId, String email, String regionCode, Address address) {
        Customer customer = new Customer(name, taxId, email, regionCode, address);
        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public Customer findById(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }
}
