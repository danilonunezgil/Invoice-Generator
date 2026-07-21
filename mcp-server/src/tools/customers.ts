import { z } from "zod";
import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { get, post } from "../httpClient.js";
import { toToolErrorResult } from "../errors.js";

interface CustomerResponse {
  id: string;
  name: string;
  taxId: string;
  email: string;
  regionCode: string;
  street: string | null;
  city: string | null;
  postalCode: string | null;
  country: string | null;
}

export function registerCustomerTools(server: McpServer) {
  server.registerTool(
    "create_customer",
    {
      title: "Crear cliente",
      description:
        "Da de alta un cliente nuevo en el sistema de facturación (POST /api/customers). " +
        "Usar antes de crear cualquier factura para ese cliente — create_invoice_draft requiere un customerId existente. " +
        "regionCode determina el IVA aplicado (TaxRule por región, 21% por defecto). No usar para buscar un cliente existente: para eso está get_customer.",
      inputSchema: {
        name: z.string().describe("Razón social o nombre completo del cliente"),
        taxId: z.string().describe("CUIT/NIF/identificador fiscal del cliente"),
        email: z.string().email(),
        regionCode: z.string().describe("Código de región usado para resolver la tasa de IVA (ej. 'ES', 'AR')"),
        street: z.string().optional(),
        city: z.string().optional(),
        postalCode: z.string().optional(),
        country: z.string().optional(),
      },
    },
    async (args) => {
      try {
        const customer = await post<CustomerResponse>("/api/customers", args);
        return { content: [{ type: "text", text: JSON.stringify(customer) }] };
      } catch (error) {
        return toToolErrorResult(error);
      }
    },
  );

  server.registerTool(
    "get_customer",
    {
      title: "Obtener cliente",
      description:
        "Obtiene los datos de un cliente existente por su UUID (GET /api/customers/{customerId}). " +
        "Usar para verificar que un cliente existe antes de facturarle, o para mostrar sus datos. Devuelve error not_found si el UUID no corresponde a ningún cliente.",
      inputSchema: {
        customerId: z.string().uuid(),
      },
    },
    async ({ customerId }) => {
      try {
        const customer = await get<CustomerResponse>(`/api/customers/${customerId}`);
        return { content: [{ type: "text", text: JSON.stringify(customer) }] };
      } catch (error) {
        return toToolErrorResult(error);
      }
    },
  );
}
