package com.danno.invoice_generator.api;

import com.danno.invoice_generator.api.dto.AddLineItemRequest;
import com.danno.invoice_generator.api.dto.CreateInvoiceRequest;
import com.danno.invoice_generator.application.ExtractedInvoiceData;
import com.danno.invoice_generator.application.ExtractedLineItem;
import com.danno.invoice_generator.application.ExtractedLineItemCategory;
import com.danno.invoice_generator.application.InvoiceService;
import com.danno.invoice_generator.domain.Address;
import com.danno.invoice_generator.domain.Customer;
import com.danno.invoice_generator.domain.Invoice;
import com.danno.invoice_generator.domain.InvoiceStatus;
import com.danno.invoice_generator.domain.exception.CustomerNotFoundException;
import com.danno.invoice_generator.domain.exception.InvalidInvoiceStateException;
import com.danno.invoice_generator.domain.exception.InvoiceNotFoundException;
import com.danno.invoice_generator.domain.exception.InvoiceNotModifiableException;
import com.danno.invoice_generator.domain.exception.InvoicePdfExtractionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InvoiceService invoiceService;

    @Test
    void given_validRequest_when_createDraft_then_returns201WithInvoiceBody() throws Exception {
        Invoice invoice = newDraftInvoice();
        given(invoiceService.createDraft(any(), any())).willReturn(invoice);
        CreateInvoiceRequest request = new CreateInvoiceRequest(UUID.randomUUID(), LocalDate.now().plusDays(30));

        mockMvc.perform(post("/api/invoices")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void given_missingCustomerId_when_createDraft_then_returns400() throws Exception {
        String body = "{\"dueDate\":\"2026-08-01\"}";

        mockMvc.perform(post("/api/invoices")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_unknownCustomer_when_createDraft_then_returns404() throws Exception {
        given(invoiceService.createDraft(any(), any()))
                .willThrow(new CustomerNotFoundException(UUID.randomUUID()));
        CreateInvoiceRequest request = new CreateInvoiceRequest(UUID.randomUUID(), LocalDate.now().plusDays(30));

        mockMvc.perform(post("/api/invoices")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void given_blankDescription_when_addLineItem_then_returns400() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        String body = "{\"description\":\"\",\"quantity\":1,\"unitPrice\":10}";

        mockMvc.perform(post("/api/invoices/{id}/line-items", invoiceId)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_paidInvoice_when_addLineItem_then_returns409() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        given(invoiceService.addLineItem(eq(invoiceId), any(), any(), any()))
                .willThrow(new InvoiceNotModifiableException(invoiceId, InvoiceStatus.PAID));
        AddLineItemRequest request = new AddLineItemRequest("Consulting", BigDecimal.ONE, BigDecimal.TEN);

        mockMvc.perform(post("/api/invoices/{id}/line-items", invoiceId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void given_cancelledInvoice_when_issueInvoice_then_returns409() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        given(invoiceService.issueInvoice(invoiceId))
                .willThrow(new InvalidInvoiceStateException(invoiceId, InvoiceStatus.CANCELLED, "issue"));

        mockMvc.perform(post("/api/invoices/{id}/issue", invoiceId))
                .andExpect(status().isConflict());
    }

    @Test
    void given_unknownInvoice_when_findById_then_returns404() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        given(invoiceService.findById(invoiceId)).willThrow(new InvoiceNotFoundException(invoiceId));

        mockMvc.perform(get("/api/invoices/{id}", invoiceId))
                .andExpect(status().isNotFound());
    }

    @Test
    void given_existingInvoice_when_findById_then_returns200WithInvoiceBody() throws Exception {
        Invoice invoice = newDraftInvoice();
        given(invoiceService.findById(any())).willReturn(invoice);

        mockMvc.perform(get("/api/invoices/{id}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void given_validPdf_when_extractFromPdf_then_returns200WithExtractedData() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        ExtractedInvoiceData data = new ExtractedInvoiceData(
                "Acme Supplies", LocalDate.of(2026, 3, 14),
                List.of(new ExtractedLineItem("Papel A4", BigDecimal.valueOf(3), new BigDecimal("18.50"),
                        ExtractedLineItemCategory.GOODS, null)),
                new BigDecimal("55.50"), true, 0);
        given(invoiceService.extractFromPdf(eq(invoiceId), any())).willReturn(data);
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", "dummy".getBytes());

        mockMvc.perform(multipart("/api/invoices/{id}/extract-from-pdf", invoiceId).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalsReconciled").value(true))
                .andExpect(jsonPath("$.vendorName").value("Acme Supplies"));
    }

    @Test
    void given_nonPdfContentType_when_extractFromPdf_then_returns400() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "invoice.txt", "text/plain", "dummy".getBytes());

        mockMvc.perform(multipart("/api/invoices/{id}/extract-from-pdf", invoiceId).file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_emptyFile_when_extractFromPdf_then_returns400() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/invoices/{id}/extract-from-pdf", invoiceId).file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_extractionServiceFailure_when_extractFromPdf_then_returns502() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        given(invoiceService.extractFromPdf(eq(invoiceId), any()))
                .willThrow(new InvoicePdfExtractionException("Claude rechazó la solicitud"));
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", "dummy".getBytes());

        mockMvc.perform(multipart("/api/invoices/{id}/extract-from-pdf", invoiceId).file(file))
                .andExpect(status().isBadGateway());
    }

    private static Invoice newDraftInvoice() {
        Customer customer = new Customer("Acme", "TAX-1", "billing@acme.test", "ES",
                new Address("Main St 1", "City", "00000", "Country"));
        return new Invoice(customer, LocalDate.now().plusDays(30));
    }
}
