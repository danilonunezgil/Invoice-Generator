package com.danno.invoice_generator.api;

import com.danno.invoice_generator.api.dto.AddLineItemRequest;
import com.danno.invoice_generator.api.dto.CreateInvoiceRequest;
import com.danno.invoice_generator.api.dto.InvoiceResponse;
import com.danno.invoice_generator.api.dto.LineItemResponse;
import com.danno.invoice_generator.application.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse createDraft(@Valid @RequestBody CreateInvoiceRequest request) {
        return InvoiceResponse.from(invoiceService.createDraft(request.customerId(), request.dueDate()));
    }

    @PostMapping("/{invoiceId}/line-items")
    @ResponseStatus(HttpStatus.CREATED)
    public LineItemResponse addLineItem(@PathVariable UUID invoiceId, @Valid @RequestBody AddLineItemRequest request) {
        return LineItemResponse.from(invoiceService.addLineItem(
                invoiceId, request.description(), request.quantity(), request.unitPrice()));
    }

    @PostMapping("/{invoiceId}/issue")
    public InvoiceResponse issueInvoice(@PathVariable UUID invoiceId) {
        return InvoiceResponse.from(invoiceService.issueInvoice(invoiceId));
    }

    @PostMapping("/{invoiceId}/pay")
    public InvoiceResponse markAsPaid(@PathVariable UUID invoiceId) {
        return InvoiceResponse.from(invoiceService.markAsPaid(invoiceId));
    }

    @PostMapping("/{invoiceId}/cancel")
    public InvoiceResponse cancelInvoice(@PathVariable UUID invoiceId) {
        return InvoiceResponse.from(invoiceService.cancelInvoice(invoiceId));
    }

    @GetMapping("/{invoiceId}")
    public InvoiceResponse findById(@PathVariable UUID invoiceId) {
        return InvoiceResponse.from(invoiceService.findById(invoiceId));
    }

    @GetMapping(value = "/{invoiceId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID invoiceId) {
        byte[] pdf = invoiceService.generatePdf(invoiceId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + invoiceId + ".pdf\"")
                .body(pdf);
    }
}
