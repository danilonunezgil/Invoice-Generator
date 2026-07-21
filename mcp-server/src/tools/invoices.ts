import { z } from "zod";
import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { get, post } from "../httpClient.js";
import { toToolErrorResult } from "../errors.js";

interface LineItemResponse {
  id: string;
  description: string;
  quantity: number;
  unitPrice: number;
  taxRate: number;
  subtotal: number;
  taxAmount: number;
  total: number;
}

interface InvoiceResponse {
  id: string;
  number: string | null;
  fiscalYear: number | null;
  customerId: string;
  status: "DRAFT" | "ISSUED" | "PAID" | "CANCELLED";
  issueDate: string | null;
  dueDate: string;
  lineItems: LineItemResponse[];
  subtotal: number;
  taxTotal: number;
  total: number;
}

function invoiceResult(invoice: InvoiceResponse) {
  return { content: [{ type: "text" as const, text: JSON.stringify(invoice) }] };
}

export function registerInvoiceTools(server: McpServer) {
  server.registerTool(
    "create_invoice_draft",
    {
      title: "Crear borrador de factura",
      description:
        "Crea una factura en estado DRAFT para un cliente existente (POST /api/invoices). " +
        "No asigna número de factura todavía — eso ocurre en issue_invoice. Requiere un customerId válido (usar get_customer o create_customer primero). " +
        "Después de crear el borrador, usar add_invoice_line_item para agregar conceptos antes de emitirla.",
      inputSchema: {
        customerId: z.string().uuid(),
        dueDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Formato esperado YYYY-MM-DD"),
      },
    },
    async (args) => {
      try {
        const invoice = await post<InvoiceResponse>("/api/invoices", args);
        return invoiceResult(invoice);
      } catch (error) {
        return toToolErrorResult(error);
      }
    },
  );

  server.registerTool(
    "add_invoice_line_item",
    {
      title: "Agregar línea a una factura",
      description:
        "Agrega un concepto (descripción, cantidad, precio unitario) a una factura en estado DRAFT " +
        "(POST /api/invoices/{invoiceId}/line-items). La tasa de IVA se resuelve automáticamente según la región del cliente y queda fija en la línea (snapshot), " +
        "aunque después cambie la TaxRule. Falla con error de tipo business si la factura ya no está en DRAFT (ISSUED/PAID/CANCELLED son inmutables).",
      inputSchema: {
        invoiceId: z.string().uuid(),
        description: z.string(),
        quantity: z.number().positive(),
        unitPrice: z.number().nonnegative(),
      },
    },
    async ({ invoiceId, ...body }) => {
      try {
        const lineItem = await post<LineItemResponse>(`/api/invoices/${invoiceId}/line-items`, body);
        return { content: [{ type: "text", text: JSON.stringify(lineItem) }] };
      } catch (error) {
        return toToolErrorResult(error);
      }
    },
  );

  server.registerTool(
    "issue_invoice",
    {
      title: "Emitir factura",
      description:
        "Transiciona una factura de DRAFT a ISSUED y le asigna un número único con formato INV-{añoFiscal}-{secuencial} " +
        "(POST /api/invoices/{invoiceId}/issue). A partir de este punto la factura ya puede descargarse en PDF vía el resource invoice://{invoiceId}/pdf. " +
        "Falla con error business si la factura no está en DRAFT.",
      inputSchema: { invoiceId: z.string().uuid() },
    },
    async ({ invoiceId }) => {
      try {
        const invoice = await post<InvoiceResponse>(`/api/invoices/${invoiceId}/issue`);
        return invoiceResult(invoice);
      } catch (error) {
        return toToolErrorResult(error);
      }
    },
  );

  server.registerTool(
    "pay_invoice",
    {
      title: "Marcar factura como pagada",
      description:
        "Transiciona una factura de ISSUED a PAID (POST /api/invoices/{invoiceId}/pay). Una vez PAID, la factura es inmutable — " +
        "ninguna otra tool puede modificarla. Falla con error business si la factura no está en ISSUED (p. ej. sigue en DRAFT o ya está CANCELLED).",
      inputSchema: { invoiceId: z.string().uuid() },
    },
    async ({ invoiceId }) => {
      try {
        const invoice = await post<InvoiceResponse>(`/api/invoices/${invoiceId}/pay`);
        return invoiceResult(invoice);
      } catch (error) {
        return toToolErrorResult(error);
      }
    },
  );

  server.registerTool(
    "cancel_invoice",
    {
      title: "Cancelar factura",
      description:
        "Cancela una factura que está en DRAFT o ISSUED (POST /api/invoices/{invoiceId}/cancel). No se puede cancelar una factura ya PAID — " +
        "en ese caso la API devuelve un error business que hay que respetar, no reintentar.",
      inputSchema: { invoiceId: z.string().uuid() },
    },
    async ({ invoiceId }) => {
      try {
        const invoice = await post<InvoiceResponse>(`/api/invoices/${invoiceId}/cancel`);
        return invoiceResult(invoice);
      } catch (error) {
        return toToolErrorResult(error);
      }
    },
  );

  server.registerTool(
    "get_invoice",
    {
      title: "Obtener factura",
      description:
        "Obtiene una factura completa con sus líneas, subtotal, total de IVA y total (GET /api/invoices/{invoiceId}). " +
        "Usar para consultar el estado actual antes de decidir la siguiente acción (issue/pay/cancel), o para responder preguntas del usuario sobre una factura puntual.",
      inputSchema: { invoiceId: z.string().uuid() },
    },
    async ({ invoiceId }) => {
      try {
        const invoice = await get<InvoiceResponse>(`/api/invoices/${invoiceId}`);
        return invoiceResult(invoice);
      } catch (error) {
        return toToolErrorResult(error);
      }
    },
  );

  server.registerTool(
    "onboard_customer_and_invoice",
    {
      title: "Alta de cliente + primera factura (agregado)",
      description:
        "Tool agregado que encadena create_customer -> create_invoice_draft -> add_invoice_line_item -> issue_invoice " +
        "para dar de alta un cliente nuevo y emitirle su primera factura en una sola llamada. " +
        "Equivalente tipado y descubrible de la skill de Claude Code 'onboard-customer-purchase' (que hace lo mismo por Bash/curl). " +
        "Preferir los tools atómicos (create_customer, create_invoice_draft, etc.) cuando el flujo necesite pasos intermedios o validación humana entre etapas.",
      inputSchema: {
        name: z.string(),
        taxId: z.string(),
        email: z.string().email(),
        regionCode: z.string(),
        dueDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
        description: z.string(),
        quantity: z.number().positive(),
        unitPrice: z.number().nonnegative(),
      },
    },
    async ({ name, taxId, email, regionCode, dueDate, description, quantity, unitPrice }) => {
      try {
        const customer = await post<{ id: string }>("/api/customers", { name, taxId, email, regionCode });
        const draft = await post<InvoiceResponse>("/api/invoices", { customerId: customer.id, dueDate });
        await post(`/api/invoices/${draft.id}/line-items`, { description, quantity, unitPrice });
        const issued = await post<InvoiceResponse>(`/api/invoices/${draft.id}/issue`);
        return invoiceResult(issued);
      } catch (error) {
        return toToolErrorResult(error);
      }
    },
  );
}
