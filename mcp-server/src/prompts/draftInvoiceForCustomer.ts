import { z } from "zod";
import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

export function registerDraftInvoicePrompt(server: McpServer) {
  server.registerPrompt(
    "draft-invoice-for-customer",
    {
      title: "Alta de cliente + factura",
      description:
        "Guía el flujo completo de alta de un cliente nuevo y emisión de su primera factura, " +
        "invocando en orden los tools create_customer, create_invoice_draft, add_invoice_line_item e issue_invoice.",
      argsSchema: {
        customerName: z.string(),
        taxId: z.string(),
        email: z.string(),
        regionCode: z.string(),
        product: z.string(),
        quantity: z.string().describe("Cantidad como string, ej. '2'"),
        unitPrice: z.string().describe("Precio unitario como string, ej. '150.00'"),
      },
    },
    ({ customerName, taxId, email, regionCode, product, quantity, unitPrice }) => ({
      messages: [
        {
          role: "user",
          content: {
            type: "text",
            text:
              `Da de alta al cliente "${customerName}" (taxId: ${taxId}, email: ${email}, regionCode: ${regionCode}) ` +
              `y emítele una factura por ${quantity} unidades de "${product}" a ${unitPrice} cada una. ` +
              "Usa en este orden: create_customer, create_invoice_draft (dueDate a 30 días desde hoy), " +
              "add_invoice_line_item, issue_invoice. Confirma el número de factura asignado al final.",
          },
        },
      ],
    }),
  );
}
