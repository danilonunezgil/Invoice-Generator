import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { ResourceTemplate } from "@modelcontextprotocol/sdk/server/mcp.js";
import { getBinary } from "../httpClient.js";

export function registerInvoicePdfResource(server: McpServer) {
  server.registerResource(
    "invoice-pdf",
    new ResourceTemplate("invoice://{invoiceId}/pdf", { list: undefined }),
    {
      title: "PDF de factura",
      description:
        "PDF de una factura emitida, generado por JasperReports (GET /api/invoices/{invoiceId}/pdf). " +
        "Solo disponible para facturas en estado ISSUED o posterior — una factura en DRAFT no tiene PDF todavía.",
      mimeType: "application/pdf",
    },
    async (uri, { invoiceId }) => {
      const { base64, contentType } = await getBinary(`/api/invoices/${invoiceId}/pdf`);
      return {
        contents: [{ uri: uri.href, mimeType: contentType, blob: base64 }],
      };
    },
  );
}
