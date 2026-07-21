import Anthropic from "@anthropic-ai/sdk";
import type { ToolResultBlockParam } from "@anthropic-ai/sdk/resources/messages";
import { runAgenticLoop } from "../agenticLoopCore.js";
import { callMcpTool, connectMcpClient, listAnthropicTools, type AnthropicTool } from "../mcpClient.js";
import { forceStructuredFinding } from "./reportFinding.js";
import type { StructuredFinding } from "./types.js";

const MODEL = "claude-sonnet-4-5";
const MCP_TOOL_NAMES = ["get_invoice", "calculate_regional_tax"] as const;

const SYSTEM_PROMPT =
  "Sos el subagente tax-validator. Tu única tarea es verificar que la tasa de IVA aplicada a " +
  "UNA factura puntual sea la correcta para la región del cliente (comparando las líneas de la " +
  "factura contra calculate_regional_tax). No conocés nada del resto del sistema de aprobación " +
  "ni del historial crediticio del cliente — solo el invoiceId y el regionCode que te dieron. " +
  "Investigá y terminá tu turno con un resumen en texto plano de lo que encontraste (todavía no reportes el hallazgo formal).";

export interface TaxValidatorInput {
  invoiceId: string;
  regionCode: string;
}

export async function runTaxValidatorSubagent(input: TaxValidatorInput): Promise<StructuredFinding> {
  const anthropic = new Anthropic();
  const mcpClient = await connectMcpClient();
  const allMcpTools = await listAnthropicTools(mcpClient);
  const tools: AnthropicTool[] = allMcpTools.filter((tool) => (MCP_TOOL_NAMES as readonly string[]).includes(tool.name));

  const { messages } = await runAgenticLoop({
    anthropic,
    model: MODEL,
    system: SYSTEM_PROMPT,
    tools,
    initialUserMessage:
      `Verificá la tasa de IVA de la factura ${input.invoiceId} (regionCode="${input.regionCode}"). ` +
      "No tenés más contexto que estos dos datos.",
    dispatchToolUseBlocks: async (blocks) => {
      const results: ToolResultBlockParam[] = [];
      for (const block of blocks) {
        const result = await callMcpTool(mcpClient, block.name, block.input as Record<string, unknown>);
        results.push({ type: "tool_result", tool_use_id: block.id, content: result.text, is_error: result.isError });
      }
      return results;
    },
  });

  return forceStructuredFinding(anthropic, MODEL, SYSTEM_PROMPT, messages);
}
