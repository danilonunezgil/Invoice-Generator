package com.danno.invoice_generator.api;

import com.danno.invoice_generator.api.dto.CreateCustomerRequest;
import com.danno.invoice_generator.api.dto.CustomerResponse;
import com.danno.invoice_generator.application.CustomerService;
import com.danno.invoice_generator.domain.Address;
import com.danno.invoice_generator.domain.Customer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        Address address = new Address(request.street(), request.city(), request.postalCode(), request.country());
        Customer customer = customerService.createCustomer(
                request.name(), request.taxId(), request.email(), request.regionCode(), address);
        return CustomerResponse.from(customer);
    }

    @GetMapping("/{customerId}")
    public CustomerResponse findById(@PathVariable UUID customerId) {
        return CustomerResponse.from(customerService.findById(customerId));
    }
}
