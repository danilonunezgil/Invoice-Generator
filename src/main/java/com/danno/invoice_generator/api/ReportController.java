package com.danno.invoice_generator.api;

import com.danno.invoice_generator.application.BillingSummaryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final BillingSummaryService billingSummaryService;

    public ReportController(BillingSummaryService billingSummaryService) {
        this.billingSummaryService = billingSummaryService;
    }

    @GetMapping(value = "/billing-summary/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadBillingSummaryPdf() {
        byte[] pdf = billingSummaryService.generatePdf();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"billing-summary.pdf\"")
                .body(pdf);
    }
}
