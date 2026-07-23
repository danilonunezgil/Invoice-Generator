package com.danno.invoice_generator.infrastructure;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.danno.invoice_generator.application.ExtractedInvoiceData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificación manual/opt-in con la API real de Claude (Fase G). Se salta automáticamente
 * (skip, no fail) tanto en `mvn test` como en `mvn verify -Pintegration` si no hay una
 * ANTHROPIC_API_KEY real en el entorno. Para correrla:
 *
 * <pre>ANTHROPIC_API_KEY=sk-ant-... ./mvnw verify -Pintegration -Dit.test=AnthropicInvoicePdfExtractionLiveIT</pre>
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicInvoicePdfExtractionLiveIT {

    @Test
    void checkMissingDateResolvesToNull() throws IOException {
        byte[] pdf = buildPdf(List.of(
                "CloudStack Hosting Inc.",
                "Concepto: Licencia anual de plataforma SaaS \"DataSync Pro\"",
                "Importe: 1200.00 USD",
                "(Este documento no incluye ninguna fecha de emision.)"));

        ExtractedInvoiceData data = realExtractor().extract(pdf);

        assertThat(data.invoiceDate()).isNull();
    }

    @Test
    void checkMismatchedTotalIsHandled() throws IOException {
        byte[] pdf = buildPdf(List.of(
                "Suministros Iberica S.L.",
                "Fecha de emision: 14/03/2026",
                "Papel A4 (caja 5 resmas)   cantidad 3   precio unitario 18.50",
                "Toner compatible HP305     cantidad 2   precio unitario 42.00",
                "TOTAL: 150.00")); // deliberadamente distinto de 3*18.50 + 2*42.00 = 139.50

        ExtractedInvoiceData data = realExtractor().extract(pdf);

        // El modelo puede autocorregirse (totalsReconciled=true) o marcarlo explícitamente
        // (totalsReconciled=false, conservando los valores tal como aparecen en el PDF) —
        // ambos son resultados válidos. Lo que nunca debe pasar es una excepción ni un total
        // fabricado que no venga ni del documento ni de la suma de líneas.
        System.out.println("checkMismatchedTotalIsHandled -> " + data);
        assertThat(data.total()).isNotNull();
    }

    private AnthropicInvoicePdfExtractor realExtractor() {
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();
        AnthropicProperties properties = new AnthropicProperties(null, "claude-opus-4-8");
        return new AnthropicInvoicePdfExtractor(client, properties, new ObjectMapper());
    }

    private byte[] buildPdf(List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                for (String line : lines) {
                    contentStream.showText(line);
                    contentStream.newLineAtOffset(0, -18);
                }
                contentStream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
