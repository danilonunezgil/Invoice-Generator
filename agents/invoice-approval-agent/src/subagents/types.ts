import { z } from "zod";
import type { AnthropicTool } from "../mcpClient.js";

/**
 * Salida estructurada que cada subagente debe producir, garantizada por `tool_choice` forzado
 * (ver reportFindingTool) en vez de depender de que el modelo devuelva JSON bien formado en
 * texto libre.
 */
export const structuredFindingSchema = z.object({
  finding: z.string(),
  confidence: z.number().min(0).max(1),
  evidence: z.array(z.string()),
});

export type StructuredFinding = z.infer<typeof structuredFindingSchema>;

export const reportFindingTool: AnthropicTool = {
  name: "report_finding",
  description: "Reporta el hallazgo final de este subagente en formato estructurado.",
  input_schema: {
    type: "object",
    properties: {
      finding: { type: "string", description: "Conclusión concisa del subagente" },
      confidence: { type: "number", minimum: 0, maximum: 1, description: "Confianza (0-1) en el hallazgo" },
      evidence: { type: "array", items: { type: "string" }, description: "Datos concretos que respaldan el hallazgo" },
    },
    required: ["finding", "confidence", "evidence"],
  },
};

export type SubagentSource = "credit-checker" | "tax-validator";

export interface SubagentOutcome {
  source: SubagentSource;
  ok: boolean;
  startedAt: number;
  finishedAt: number;
  finding?: StructuredFinding;
  error?: {
    failure_type: "timeout" | "error";
    partial_results: null;
    message: string;
  };
}
