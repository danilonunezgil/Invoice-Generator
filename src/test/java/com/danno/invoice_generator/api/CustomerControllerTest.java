package com.danno.invoice_generator.api;

import com.danno.invoice_generator.api.dto.CreateCustomerRequest;
import com.danno.invoice_generator.application.CustomerService;
import com.danno.invoice_generator.domain.Address;
import com.danno.invoice_generator.domain.Customer;
import com.danno.invoice_generator.domain.exception.CustomerNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerService customerService;

    @Test
    void given_validRequest_when_createCustomer_then_returns201WithCustomerBody() throws Exception {
        given(customerService.createCustomer(any(), any(), any(), any(), any())).willReturn(newCustomer());
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Acme", "TAX-1", "billing@acme.test", "ES", "Main St 1", "City", "00000", "Country");

        mockMvc.perform(post("/api/customers")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme"));
    }

    @Test
    void given_blankName_when_createCustomer_then_returns400() throws Exception {
        String body = "{\"name\":\"\",\"taxId\":\"TAX-1\",\"email\":\"billing@acme.test\",\"regionCode\":\"ES\"}";

        mockMvc.perform(post("/api/customers")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_invalidEmail_when_createCustomer_then_returns400() throws Exception {
        String body = "{\"name\":\"Acme\",\"taxId\":\"TAX-1\",\"email\":\"not-an-email\",\"regionCode\":\"ES\"}";

        mockMvc.perform(post("/api/customers")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_unknownCustomer_when_findById_then_returns404() throws Exception {
        UUID customerId = UUID.randomUUID();
        given(customerService.findById(customerId)).willThrow(new CustomerNotFoundException(customerId));

        mockMvc.perform(get("/api/customers/{id}", customerId))
                .andExpect(status().isNotFound());
    }

    @Test
    void given_existingCustomer_when_findById_then_returns200WithCustomerBody() throws Exception {
        given(customerService.findById(any())).willReturn(newCustomer());

        mockMvc.perform(get("/api/customers/{id}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme"));
    }

    private static Customer newCustomer() {
        return new Customer("Acme", "TAX-1", "billing@acme.test", "ES",
                new Address("Main St 1", "City", "00000", "Country"));
    }
}
