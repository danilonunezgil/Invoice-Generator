package com.danno.invoice_generator.infrastructure;

import com.danno.invoice_generator.application.BillingSummary;
import com.danno.invoice_generator.application.port.BillingSummaryReportGenerator;
import com.danno.invoice_generator.domain.exception.BillingSummaryPdfGenerationException;
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
import java.util.HashMap;
import java.util.Map;

@Component
public class JasperBillingSummaryPdfGenerator implements BillingSummaryReportGenerator {

    private final JasperReport billingSummaryReport;
    private final JasperReport statusBreakdownSubreport;

    public JasperBillingSummaryPdfGenerator() {
        this.billingSummaryReport = loadReport("reports/billing-summary.jasper");
        this.statusBreakdownSubreport = loadReport("reports/sub_status_breakdown.jasper");
    }

    @Override
    public byte[] generate(BillingSummary summary) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("totalCustomers", summary.getTotalCustomers());
        parameters.put("totalInvoices", summary.getTotalInvoices());
        parameters.put("statusBreakdown", summary.getStatusBreakdown());
        parameters.put("SUB_STATUS_BREAKDOWN", statusBreakdownSubreport);

        try {
            JasperPrint jasperPrint = JasperFillManager.fillReport(billingSummaryReport, parameters, new JREmptyDataSource(1));
            return JasperExportManager.exportReportToPdf(jasperPrint);
        } catch (JRException e) {
            throw new BillingSummaryPdfGenerationException(e);
        }
    }

    private JasperReport loadReport(String classpathLocation) {
        try (InputStream is = new ClassPathResource(classpathLocation).getInputStream()) {
            return (JasperReport) JRLoader.loadObject(is);
        } catch (IOException | JRException e) {
            throw new IllegalStateException("No se pudo cargar la plantilla Jasper: " + classpathLocation, e);
        }
    }
}
