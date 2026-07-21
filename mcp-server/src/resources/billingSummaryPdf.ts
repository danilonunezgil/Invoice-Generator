import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { getBinary } from "../httpClient.js";

export function registerBillingSummaryResource(server: McpServer) {
  server.registerResource(
    "billing-summary-pdf",
    "report://billing-summary/pdf",
    {
      title: "PDF de resumen de facturación",
      description:
        "Reporte agregado con el conteo de clientes y de facturas por estado (GET /api/reports/billing-summary/pdf). " +
        "Útil para adjuntar al contexto sin necesitar una tool call exploratoria adicional.",
      mimeType: "application/pdf",
    },
    async (uri) => {
      const { base64, contentType } = await getBinary("/api/reports/billing-summary/pdf");
      return {
        contents: [{ uri: uri.href, mimeType: contentType, blob: base64 }],
      };
    },
  );
}
