package com.danno.invoice_generator.infrastructure;

import com.danno.invoice_generator.application.port.InvoicePdfGenerator;
import com.danno.invoice_generator.domain.Address;
import com.danno.invoice_generator.domain.Invoice;
import com.danno.invoice_generator.domain.exception.InvoicePdfGenerationException;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.util.JRLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
public class JasperInvoicePdfGenerator implements InvoicePdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JasperReport invoiceReport;
    private final JasperReport lineItemsSubreport;

    public JasperInvoicePdfGenerator() {
        this.invoiceReport = loadReport("reports/invoice.jasper");
        this.lineItemsSubreport = loadReport("reports/sub_line_items.jasper");
    }

    @Override
    public byte[] generate(Invoice invoice) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("invoiceNumber", invoice.getNumber().value());
        parameters.put("statusLabel", invoice.getStatus().name());
        parameters.put("issueDate", invoice.getIssueDate().format(DATE_FORMAT));
        parameters.put("dueDate", invoice.getDueDate().format(DATE_FORMAT));
        parameters.put("customerName", invoice.getCustomer().getName());
        parameters.put("customerTaxId", invoice.getCustomer().getTaxId());
        parameters.put("customerEmail", invoice.getCustomer().getEmail());
        parameters.put("customerAddress", formatAddress(invoice.getCustomer().getAddress()));
        parameters.put("subtotal", invoice.getSubtotal());
        parameters.put("taxTotal", invoice.getTaxTotal());
        parameters.put("total", invoice.getTotal());
        parameters.put("lineItems", invoice.getLineItems());
        parameters.put("SUB_LINE_ITEMS", lineItemsSubreport);

        try {
            JasperPrint jasperPrint = JasperFillManager.fillReport(invoiceReport, parameters, new JREmptyDataSource(1));
            return JasperExportManager.exportReportToPdf(jasperPrint);
        } catch (JRException e) {
            throw new InvoicePdfGenerationException(invoice.getId(), e);
        }
    }

    private String formatAddress(Address address) {
        return "%s, %s %s, %s".formatted(
                address.getStreet(), address.getCity(), address.getPostalCode(), address.getCountry());
    }

    private JasperReport loadReport(String classpathLocation) {
        try (InputStream is = new ClassPathResource(classpathLocation).getInputStream()) {
            return (JasperReport) JRLoader.loadObject(is);
        } catch (IOException | JRException e) {
            throw new IllegalStateException("No se pudo cargar la plantilla Jasper: " + classpathLocation, e);
        }
    }
}
