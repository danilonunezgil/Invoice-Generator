package com.danno.invoice_generator.application;

import com.danno.invoice_generator.PostgresIntegrationTest;
import com.danno.invoice_generator.domain.Address;
import com.danno.invoice_generator.domain.Customer;
import com.danno.invoice_generator.domain.exception.CustomerNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class CustomerServiceIT extends PostgresIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Test
    void given_validData_when_createCustomer_then_customerIsPersistedWithGeneratedId() {
        Customer customer = customerService.createCustomer(
                "Acme", "TAX-1", "billing@acme.test", "ES",
                new Address("Main St 1", "City", "00000", "Country"));

        assertThat(customer.getId()).isNotNull();
        assertThat(customer.getName()).isEqualTo("Acme");
        assertThat(customer.getRegionCode()).isEqualTo("ES");
    }

    @Test
    void given_persistedCustomer_when_findById_then_returnsCustomer() {
        Customer created = customerService.createCustomer(
                "Acme", "TAX-1", "billing@acme.test", "ES",
                new Address("Main St 1", "City", "00000", "Country"));

        Customer found = customerService.findById(created.getId());

        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getEmail()).isEqualTo("billing@acme.test");
    }

    @Test
    void given_unknownCustomerId_when_findById_then_throwsCustomerNotFoundException() {
        assertThatThrownBy(() -> customerService.findById(UUID.randomUUID()))
                .isInstanceOf(CustomerNotFoundException.class);
    }
}
