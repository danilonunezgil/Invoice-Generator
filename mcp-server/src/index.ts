import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { registerCustomerTools } from "./tools/customers.js";
import { registerInvoiceTools } from "./tools/invoices.js";
import { registerInvoicePdfResource } from "./resources/invoicePdf.js";
import { registerBillingSummaryResource } from "./resources/billingSummaryPdf.js";
import { registerDraftInvoicePrompt } from "./prompts/draftInvoiceForCustomer.js";

const server = new McpServer({
  name: "invoice-api",
  version: "1.0.0",
});

registerCustomerTools(server);
registerInvoiceTools(server);
registerInvoicePdfResource(server);
registerBillingSummaryResource(server);
registerDraftInvoicePrompt(server);

const transport = new StdioServerTransport();
await server.connect(transport);
