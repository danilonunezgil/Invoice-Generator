package com.danno.invoice_generator.infrastructure;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.CacheCreation;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.DirectCaller;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RefusalStopDetails;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import com.danno.invoice_generator.application.ExtractedInvoiceData;
import com.danno.invoice_generator.domain.exception.InvoicePdfExtractionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AnthropicInvoicePdfExtractorTest {

    private AnthropicClient client;
    private MessageService messageService;
    private AnthropicInvoicePdfExtractor extractor;

    @BeforeEach
    void setUp() {
        client = mock(AnthropicClient.class);
        messageService = mock(MessageService.class);
        given(client.messages()).willReturn(messageService);
        AnthropicProperties properties = new AnthropicProperties("test-key", "claude-opus-4-8");
        extractor = new AnthropicInvoicePdfExtractor(client, properties, new ObjectMapper());
    }

    @Test
    void given_firstResponseReconciles_when_extract_then_returnsWithZeroRetries() {
        Message response = toolUseMessage(extractionInput("Acme Supplies", "2026-03-14",
                new BigDecimalLineItem("Papel A4", "3", "18.50", "goods", null), "55.50"));
        given(messageService.create(any(MessageCreateParams.class))).willReturn(response);

        ExtractedInvoiceData data = extractor.extract("dummy-pdf-bytes".getBytes());

        assertThat(data.retriesUsed()).isZero();
        assertThat(data.totalsReconciled()).isTrue();
        assertThat(data.vendorName()).isEqualTo("Acme Supplies");
        verify(messageService, times(1)).create(any(MessageCreateParams.class));
    }

    @Test
    void given_mismatchOnFirstAttempt_when_extract_then_correctsOnSecondAttempt() {
        Message mismatch = toolUseMessage(extractionInput("Acme Supplies", "2026-03-14",
                new BigDecimalLineItem("Papel A4", "3", "18.50", "goods", null), "100.00"));
        Message corrected = toolUseMessage(extractionInput("Acme Supplies", "2026-03-14",
                new BigDecimalLineItem("Papel A4", "3", "18.50", "goods", null), "55.50"));
        given(messageService.create(any(MessageCreateParams.class))).willReturn(mismatch, corrected);

        ExtractedInvoiceData data = extractor.extract("dummy-pdf-bytes".getBytes());

        assertThat(data.retriesUsed()).isEqualTo(1);
        assertThat(data.totalsReconciled()).isTrue();
        verify(messageService, times(2)).create(any(MessageCreateParams.class));
    }

    @Test
    void given_persistentMismatch_when_extract_then_returnsWithReconciledFalseAfterMaxRetries() {
        Message mismatch = toolUseMessage(extractionInput("Acme Supplies", "2026-03-14",
                new BigDecimalLineItem("Papel A4", "3", "18.50", "goods", null), "100.00"));
        given(messageService.create(any(MessageCreateParams.class))).willReturn(mismatch, mismatch, mismatch);

        ExtractedInvoiceData data = extractor.extract("dummy-pdf-bytes".getBytes());

        assertThat(data.retriesUsed()).isEqualTo(2);
        assertThat(data.totalsReconciled()).isFalse();
        verify(messageService, times(3)).create(any(MessageCreateParams.class));
    }

    @Test
    void given_refusalStopReason_when_extract_then_throwsInvoicePdfExtractionException() {
        Message refusal = Message.builder()
                .id("msg_refusal")
                .model("claude-opus-4-8")
                .content(List.of())
                .stopReason(StopReason.REFUSAL)
                .stopDetails(RefusalStopDetails.builder()
                        .category(RefusalStopDetails.Category.GENERAL_HARMS)
                        .explanation("contenido no permitido")
                        .build())
                .stopSequence(Optional.empty())
                .usage(minimalUsage())
                .build();
        given(messageService.create(any(MessageCreateParams.class))).willReturn(refusal);

        assertThatThrownBy(() -> extractor.extract("dummy-pdf-bytes".getBytes()))
                .isInstanceOf(InvoicePdfExtractionException.class);
    }

    @Test
    void given_maxTokensStopReason_when_extract_then_throwsInvoicePdfExtractionException() {
        Message truncated = Message.builder()
                .id("msg_truncated")
                .model("claude-opus-4-8")
                .content(List.of())
                .stopReason(StopReason.MAX_TOKENS)
                .stopDetails(Optional.empty())
                .stopSequence(Optional.empty())
                .usage(minimalUsage())
                .build();
        given(messageService.create(any(MessageCreateParams.class))).willReturn(truncated);

        assertThatThrownBy(() -> extractor.extract("dummy-pdf-bytes".getBytes()))
                .isInstanceOf(InvoicePdfExtractionException.class);
    }

    private Message toolUseMessage(Map<String, Object> input) {
        ToolUseBlock toolUse = ToolUseBlock.builder()
                .id("toolu_test")
                .name("extract_invoice_data")
                .input(JsonValue.from(input))
                .caller(DirectCaller.builder().build())
                .build();
        return Message.builder()
                .id("msg_test")
                .model("claude-opus-4-8")
                .content(List.of(ContentBlock.ofToolUse(toolUse)))
                .stopReason(StopReason.TOOL_USE)
                .stopDetails(Optional.empty())
                .stopSequence(Optional.empty())
                .usage(minimalUsage())
                .build();
    }

    private Usage minimalUsage() {
        return Usage.builder()
                .inputTokens(100)
                .outputTokens(50)
                .cacheCreation(CacheCreation.builder()
                        .ephemeral1hInputTokens(0)
                        .ephemeral5mInputTokens(0)
                        .build())
                .cacheCreationInputTokens(0L)
                .cacheReadInputTokens(0L)
                .inferenceGeo(Optional.empty())
                .outputTokensDetails(Optional.empty())
                .serverToolUse(Optional.empty())
                .serviceTier(Optional.empty())
                .build();
    }

    private Map<String, Object> extractionInput(String vendorName, String invoiceDate,
                                                 BigDecimalLineItem lineItem, String total) {
        Map<String, Object> line = new HashMap<>();
        line.put("description", lineItem.description);
        line.put("quantity", new java.math.BigDecimal(lineItem.quantity));
        line.put("unitPrice", new java.math.BigDecimal(lineItem.unitPrice));
        line.put("category", lineItem.category);
        line.put("categoryDetail", lineItem.categoryDetail);

        Map<String, Object> input = new HashMap<>();
        input.put("vendorName", vendorName);
        input.put("invoiceDate", invoiceDate);
        input.put("lineItems", List.of(line));
        input.put("total", new java.math.BigDecimal(total));
        return input;
    }

    private record BigDecimalLineItem(String description, String quantity, String unitPrice,
                                       String category, String categoryDetail) {
    }
}
