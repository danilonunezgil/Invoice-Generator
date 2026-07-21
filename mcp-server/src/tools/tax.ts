import { z } from "zod";
import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { get } from "../httpClient.js";
import { toToolErrorResult } from "../errors.js";

interface TaxRuleResponse {
  regionCode: string;
  rate: number;
  isDefault: boolean;
}

export function registerTaxTools(server: McpServer) {
  server.registerTool(
    "calculate_regional_tax",
    {
      title: "Calcular tasa de IVA por región",
      description:
        "Resuelve la tasa de IVA configurada para un regionCode (GET /api/tax-rules/{regionCode}). " +
        "Si no hay una TaxRule para esa región, devuelve la tasa por defecto (21%) con isDefault=true. " +
        "Es la misma resolución que add_invoice_line_item aplica internamente al agregar una línea — usar esta tool para " +
        "consultar la tasa de antemano sin necesidad de crear una factura.",
      inputSchema: {
        regionCode: z.string().describe("Código de región del cliente (ej. 'ES', 'AR')"),
      },
    },
    async ({ regionCode }) => {
      try {
        const taxRule = await get<TaxRuleResponse>(`/api/tax-rules/${encodeURIComponent(regionCode)}`);
        return { content: [{ type: "text" as const, text: JSON.stringify(taxRule) }] };
      } catch (error) {
        return toToolErrorResult(error);
      }
    },
  );
}
