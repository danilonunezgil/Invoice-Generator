import type { AnthropicTool } from "../mcpClient.js";
import { GuardBlockedError, assertAutoApprovalAllowed } from "../guard.js";

/**
 * Tool LOCAL (no pasa por el mcp-server/MCP): es la acción final y terminal de este agente
 * — "aprobar" no es un endpoint real de la API de facturación, es la decisión del agente.
 * Por eso el guard vive acá, en el executor de la tool, y no en el mcp-server: el guard
 * protege la ÚNICA acción de este agente que tiene consecuencia (aprobar sin supervisión),
 * no las tools de solo lectura ni request_human_approval (que ya es, en sí misma, la vía segura).
 */
export const approveInvoiceTool: AnthropicTool = {
  name: "approve_invoice",
  description:
    "Aprueba automáticamente una factura sin intervención humana. Está sujeta a un guard programático " +
    "que la bloquea si el total excede el umbral de autoaprobación — si eso pasa, usar request_human_approval " +
    "en su lugar. No usar para facturas que ya requieren revisión humana.",
  input_schema: {
    type: "object",
    properties: {
      invoiceId: { type: "string", format: "uuid" },
      total: { type: "number", description: "Total de la factura (subtotal + IVA)" },
    },
    required: ["invoiceId", "total"],
  },
};

export function executeApproveInvoice(input: { invoiceId: string; total: number }): {
  isError: boolean;
  text: string;
} {
  try {
    assertAutoApprovalAllowed(input.total);
  } catch (error) {
    if (error instanceof GuardBlockedError) {
      return { isError: true, text: error.message };
    }
    throw error;
  }

  return {
    isError: false,
    text: JSON.stringify({ status: "approved", invoiceId: input.invoiceId, total: input.total }),
  };
}
