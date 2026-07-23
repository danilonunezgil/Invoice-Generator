package com.danno.invoice_generator.infrastructure;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.RefusalStopDetails;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoiceTool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.danno.invoice_generator.application.ExtractedInvoiceData;
import com.danno.invoice_generator.application.ExtractedLineItem;
import com.danno.invoice_generator.application.ExtractedLineItemCategory;
import com.danno.invoice_generator.application.port.InvoicePdfExtractor;
import com.danno.invoice_generator.domain.exception.InvoicePdfExtractionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AnthropicInvoicePdfExtractor implements InvoicePdfExtractor {

    private static final String EXTRACTION_TOOL_NAME = "extract_invoice_data";
    private static final long MAX_TOKENS = 4096L;

    // Red de seguridad para no encadenar llamadas indefinidamente si el modelo nunca logra
    // reconciliar el total con la suma de las líneas — nunca es la lógica que decide que la
    // extracción "fracasó": agotados los reintentos devolvemos igual el resultado con
    // totalsReconciled=false, en vez de lanzar una excepción.
    private static final int MAX_RETRIES = 2;
    private static final BigDecimal RECONCILIATION_TOLERANCE = new BigDecimal("0.01");

    private static final String EXTRACTION_SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "vendorName": { "type": ["string", "null"], "description": "Nombre del proveedor/emisor tal como aparece en el documento. null si no aparece explícitamente." },
                "invoiceDate": { "type": ["string", "null"], "description": "Fecha de emisión en ISO-8601 (YYYY-MM-DD). null si no aparece explícitamente en el PDF. NUNCA inventes ni infieras una fecha (no uses la fecha actual ni la de vencimiento como sustituto)." },
                "lineItems": {
                  "type": "array",
                  "description": "Líneas de detalle. Si el documento no tiene tabla (texto corrido), extrae igualmente una o más líneas a partir del texto, o un array vacío si no hay ningún detalle reconocible.",
                  "items": {
                    "type": "object",
                    "properties": {
                      "description": { "type": ["string", "null"] },
                      "quantity": { "type": ["number", "null"], "description": "null si no aparece explícitamente — no asumas 1 por defecto." },
                      "unitPrice": { "type": ["number", "null"] },
                      "category": { "type": "string", "enum": ["goods", "services", "travel", "software_license", "consulting", "utilities", "other"] },
                      "categoryDetail": { "type": ["string", "null"], "description": "No-null SOLO cuando category=='other'; en cualquier otro caso debe ser null." }
                    },
                    "required": ["description", "quantity", "unitPrice", "category", "categoryDetail"]
                  }
                },
                "total": { "type": ["number", "null"], "description": "Importe total tal como aparece impreso, no calculado por ti. null si no aparece explícitamente." }
              },
              "required": ["vendorName", "invoiceDate", "lineItems", "total"]
            }
            """;

    private static final String SYSTEM_PROMPT = """
            Eres un asistente experto en extracción de datos de facturas de proveedores a partir de documentos PDF.

            Tu única tarea es invocar la tool `extract_invoice_data` con los datos que aparecen EXPLÍCITAMENTE en el PDF adjunto.

            REGLAS CRÍTICAS:
            1. Nunca inventes, calcules ni infieras un valor que no aparezca explícitamente en el documento. Si un dato no está presente, usa null para ese campo — pero el campo (la key) SIEMPRE debe estar presente en tu respuesta.
            2. En particular, la fecha de la factura (`invoiceDate`) debe ser null si no aparece explícitamente en el PDF. No la sustituyas por la fecha de vencimiento, la fecha actual, ni ninguna otra fecha relacionada.
            3. El campo `total` debe reflejar el importe total tal como aparece impreso en el documento, no un cálculo que tú hagas sumando las líneas.
            4. Para cada línea, asigna la categoría (`category`) más adecuada de la lista permitida. Usa `other` solo cuando ninguna categoría conocida aplique, y en ese caso completa `categoryDetail` con una breve explicación de qué es realmente ese concepto. Para cualquier otra categoría, `categoryDetail` debe ser null.
            5. Si el PDF no tiene una tabla de líneas detallada (por ejemplo, es una factura de texto corrido/narrativa), extrae igualmente la información en forma de una o más líneas de `lineItems` a partir del texto, sin inventar cantidades o precios que no se mencionen.
            6. Si en un intento anterior te informamos de que el total no coincide con la suma de las líneas, revisa el PDF de nuevo y corrige el campo que esté equivocado — pero solo si puedes justificar la corrección con lo que ves en el documento. Si no puedes reconciliarlo, devuelve los valores tal como los ves en el PDF; no fuerces una coincidencia artificial.

            A continuación tienes ejemplos de cómo debes interpretar distintos formatos de factura. Los ejemplos son ilustrativos del razonamiento esperado — no son documentos reales adjuntos a esta conversación.

            --- EJEMPLO 1: factura en formato tabla ---
            Extracto del PDF:
              Proveedor: Suministros Ibérica S.L.
              Fecha de emisión: 14/03/2026
              Descripción                  Cantidad   Precio unitario
              Papel A4 (caja 5 resmas)         3           18.50
              Tóner compatible HP305           2           42.00
              TOTAL: 139.50

            Llamada esperada a la tool:
            {
              "vendorName": "Suministros Ibérica S.L.",
              "invoiceDate": "2026-03-14",
              "lineItems": [
                {"description": "Papel A4 (caja 5 resmas)", "quantity": 3, "unitPrice": 18.50, "category": "goods", "categoryDetail": null},
                {"description": "Tóner compatible HP305", "quantity": 2, "unitPrice": 42.00, "category": "goods", "categoryDetail": null}
              ],
              "total": 139.50
            }
            (3×18.50 + 2×42.00 = 55.50 + 84.00 = 139.50 — el total declarado coincide con la suma de las líneas.)

            --- EJEMPLO 2: factura en formato texto corrido (sin tabla) ---
            Extracto del PDF:
              "Global Consulting Partners LLC le factura por los servicios de consultoría estratégica
               prestados durante el mes de febrero de 2026, correspondientes a 40 horas de asesoría
               a razón de 95.00 USD por hora. Fecha de la factura: 02/03/2026. Importe total a pagar: 3800.00 USD."

            Llamada esperada a la tool:
            {
              "vendorName": "Global Consulting Partners LLC",
              "invoiceDate": "2026-03-02",
              "lineItems": [
                {"description": "Servicios de consultoría estratégica (febrero 2026)", "quantity": 40, "unitPrice": 95.00, "category": "consulting", "categoryDetail": null}
              ],
              "total": 3800.00
            }
            (40×95.00 = 3800.00 — coincide, aunque no hubiera tabla explícita.)

            --- EJEMPLO 3: factura con un campo ausente (fecha no impresa en el documento) ---
            Extracto del PDF:
              Proveedor: CloudStack Hosting Inc.
              Concepto: Licencia anual de plataforma SaaS "DataSync Pro"
              Importe: 1200.00 USD
              (El documento no incluye ninguna fecha de emisión ni de facturación en ningún lugar del texto.)

            Llamada esperada a la tool:
            {
              "vendorName": "CloudStack Hosting Inc.",
              "invoiceDate": null,
              "lineItems": [
                {"description": "Licencia anual de plataforma SaaS \\"DataSync Pro\\"", "quantity": 1, "unitPrice": 1200.00, "category": "software_license", "categoryDetail": null}
              ],
              "total": 1200.00
            }
            (Nótese que `invoiceDate` es null porque el documento no la menciona — NO se debe rellenar con una fecha estimada o la fecha actual.)

            Recuerda: tu respuesta debe ser siempre una única llamada a la tool `extract_invoice_data` con todos los campos presentes (usando null donde corresponda).
            """;

    private final AnthropicClient client;
    private final AnthropicProperties properties;
    private final ObjectMapper objectMapper;
    private final Tool extractionTool;

    public AnthropicInvoicePdfExtractor(AnthropicClient client, AnthropicProperties properties, ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.extractionTool = buildExtractionTool();
    }

    @Override
    public ExtractedInvoiceData extract(byte[] pdfBytes) {
        String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);
        List<MessageParam> messages = new ArrayList<>();
        messages.add(buildInitialUserMessage(base64Pdf));

        RawExtractedInvoiceData raw;
        boolean reconciled;
        int attempt = 0;

        while (true) {
            Message response = callClaude(messages);
            requireUsableStopReason(response);
            ToolUseBlock toolUse = requireToolUse(response);
            raw = parseToolInput(toolUse);

            BigDecimal sumLines = sumLineItems(raw);
            reconciled = raw.total() == null
                    || sumLines.subtract(raw.total()).abs().compareTo(RECONCILIATION_TOLERANCE) <= 0;

            // Se conserva el turno assistant completo (incluido el tool_use) para que la
            // conversación siga siendo válida si hace falta un reintento adicional.
            messages.add(response.toParam());

            if (reconciled || attempt >= MAX_RETRIES) {
                break;
            }
            attempt++;
            messages.add(buildMismatchToolResultMessage(toolUse, raw, sumLines));
        }

        return toInternal(raw, reconciled, attempt);
    }

    private Message callClaude(List<MessageParam> messages) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT)
                // tool_choice forzado a la tool de extracción específica, en cada llamada
                // (incluidos los reintentos): nunca dejamos que el modelo decida no usarla.
                .toolChoice(ToolChoiceTool.builder().name(EXTRACTION_TOOL_NAME).build())
                .addTool(extractionTool)
                .messages(messages)
                .build();
        try {
            return client.messages().create(params);
        } catch (RuntimeException e) {
            throw new InvoicePdfExtractionException(
                    "Fallo al llamar a la API de Claude para extraer la factura: " + e.getMessage(), e);
        }
    }

    private void requireUsableStopReason(Message response) {
        StopReason stopReason = response.stopReason()
                .orElseThrow(() -> new InvoicePdfExtractionException("La respuesta de Claude no incluyó stop_reason."));

        if (stopReason.equals(StopReason.REFUSAL)) {
            String detail = response.stopDetails()
                    .flatMap(RefusalStopDetails::explanation)
                    .orElse("sin detalle");
            throw new InvoicePdfExtractionException("Claude rechazó la solicitud de extracción: " + detail);
        }
        if (stopReason.equals(StopReason.MAX_TOKENS)) {
            // Nunca se trata max_tokens como una decisión final: la respuesta fue truncada
            // antes de completar la extracción, no hay nada estructurado que usar.
            throw new InvoicePdfExtractionException(
                    "La respuesta de Claude fue truncada (stop_reason=max_tokens) antes de completar la extracción.");
        }
    }

    private ToolUseBlock requireToolUse(Message response) {
        return response.content().stream()
                .map(ContentBlock::toolUse)
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new InvoicePdfExtractionException(
                        "La respuesta de Claude no incluyó una llamada a la tool de extracción."));
    }

    private RawExtractedInvoiceData parseToolInput(ToolUseBlock toolUse) {
        JsonNode node = toolUse._input().convert(JsonNode.class);
        try {
            return objectMapper.treeToValue(node, RawExtractedInvoiceData.class);
        } catch (JsonProcessingException e) {
            throw new InvoicePdfExtractionException(
                    "No se pudo interpretar el JSON devuelto por la tool de extracción.", e);
        }
    }

    private BigDecimal sumLineItems(RawExtractedInvoiceData raw) {
        return raw.lineItems().stream()
                .filter(item -> item.quantity() != null && item.unitPrice() != null)
                .map(item -> item.quantity().multiply(item.unitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private MessageParam buildMismatchToolResultMessage(ToolUseBlock toolUse, RawExtractedInvoiceData raw, BigDecimal sumLines) {
        String detail = ("El total declarado (%s) no coincide con la suma de las líneas (%s). "
                + "Corrige el campo `total` o los importes de las líneas basándote únicamente en lo que aparece "
                + "en el PDF. No inventes valores nuevos.")
                .formatted(raw.total(), sumLines);
        return MessageParam.builder()
                .role(MessageParam.Role.USER)
                .contentOfBlockParams(List.of(ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder()
                                .toolUseId(toolUse.id())
                                .isError(true)
                                .content(detail)
                                .build())))
                .build();
    }

    private MessageParam buildInitialUserMessage(String base64Pdf) {
        DocumentBlockParam document = DocumentBlockParam.builder()
                .base64Source(base64Pdf)
                .title("Factura de proveedor")
                .build();
        TextBlockParam instruction = TextBlockParam.builder()
                .text("Extrae los datos de esta factura de proveedor usando la tool extract_invoice_data.")
                .build();
        return MessageParam.builder()
                .role(MessageParam.Role.USER)
                .contentOfBlockParams(List.of(ContentBlockParam.ofDocument(document), ContentBlockParam.ofText(instruction)))
                .build();
    }

    private ExtractedInvoiceData toInternal(RawExtractedInvoiceData raw, boolean reconciled, int retriesUsed) {
        List<ExtractedLineItem> lineItems = raw.lineItems().stream()
                .map(item -> new ExtractedLineItem(
                        item.description(),
                        item.quantity(),
                        item.unitPrice(),
                        ExtractedLineItemCategory.valueOf(item.category().toUpperCase()),
                        item.categoryDetail()))
                .toList();
        LocalDate invoiceDate = raw.invoiceDate() == null ? null : LocalDate.parse(raw.invoiceDate());
        return new ExtractedInvoiceData(raw.vendorName(), invoiceDate, lineItems, raw.total(), reconciled, retriesUsed);
    }

    private Tool buildExtractionTool() {
        try {
            JsonNode schema = objectMapper.readTree(EXTRACTION_SCHEMA_JSON);
            Map<String, Object> schemaProperties = objectMapper.convertValue(
                    schema.get("properties"), new TypeReference<Map<String, Object>>() {
                    });
            List<String> required = objectMapper.convertValue(
                    schema.get("required"), new TypeReference<List<String>>() {
                    });

            Tool.InputSchema.Properties.Builder propsBuilder = Tool.InputSchema.Properties.builder();
            schemaProperties.forEach((key, value) -> propsBuilder.putAdditionalProperty(key, JsonValue.from(value)));

            return Tool.builder()
                    .name(EXTRACTION_TOOL_NAME)
                    .description("Extrae los datos estructurados de una factura de proveedor a partir de su PDF.")
                    .inputSchema(Tool.InputSchema.builder()
                            .properties(propsBuilder.build())
                            .required(required)
                            .build())
                    .build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Esquema de extracción de factura inválido", e);
        }
    }

    private record RawExtractedLineItem(
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String category,
            String categoryDetail) {
    }

    private record RawExtractedInvoiceData(
            String vendorName,
            String invoiceDate,
            List<RawExtractedLineItem> lineItems,
            BigDecimal total) {
    }
}
