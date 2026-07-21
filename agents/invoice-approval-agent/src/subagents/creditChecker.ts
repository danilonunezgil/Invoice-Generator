import Anthropic from "@anthropic-ai/sdk";
import type { ToolResultBlockParam } from "@anthropic-ai/sdk/resources/messages";
import { runAgenticLoop } from "../agenticLoopCore.js";
import { callMcpTool, connectMcpClient, listAnthropicTools, type AnthropicTool } from "../mcpClient.js";
import { forceStructuredFinding } from "./reportFinding.js";
import type { StructuredFinding } from "./types.js";

const MODEL = "claude-sonnet-4-5";
const MCP_TOOL_NAMES = ["get_invoice", "list_overdue_invoices"] as const;

const SYSTEM_PROMPT =
  "Sos el subagente credit-checker. Tu única tarea es evaluar el historial crediticio de UN " +
  "cliente puntual: cuántas facturas ISSUED tiene vencidas (list_overdue_invoices) y qué tan " +
  "grave es eso para aprobar una factura nueva. No conocés nada del resto del sistema de " +
  "aprobación ni de otros clientes — solo el customerId que te dieron. Investigá y terminá tu " +
  "turno con un resumen en texto plano de lo que encontraste (todavía no reportes el hallazgo formal).";

export interface CreditCheckerInput {
  customerId: string;
}

export async function runCreditCheckerSubagent(input: CreditCheckerInput): Promise<StructuredFinding> {
  // Simula un timeout de red/proveedor para poder probar el manejo de fallos parciales del coordinador.
  const simulatedDelayMs = Number(process.env.SIMULATE_CREDIT_CHECKER_TIMEOUT_MS ?? "0");
  if (simulatedDelayMs > 0) {
    await new Promise((resolve) => setTimeout(resolve, simulatedDelayMs));
  }

  const anthropic = new Anthropic();
  const mcpClient = await connectMcpClient();
  const allMcpTools = await listAnthropicTools(mcpClient);
  const tools: AnthropicTool[] = allMcpTools.filter((tool) => (MCP_TOOL_NAMES as readonly string[]).includes(tool.name));

  const { messages } = await runAgenticLoop({
    anthropic,
    model: MODEL,
    system: SYSTEM_PROMPT,
    tools,
    initialUserMessage: `Evaluá el historial crediticio del cliente ${input.customerId}. No tenés más contexto que este customerId.`,
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
