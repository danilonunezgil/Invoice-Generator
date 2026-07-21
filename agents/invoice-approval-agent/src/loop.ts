import Anthropic from "@anthropic-ai/sdk";
import type { ToolResultBlockParam, ToolUseBlock } from "@anthropic-ai/sdk/resources/messages";
import { runAgenticLoop } from "./agenticLoopCore.js";
import { AUTO_APPROVAL_THRESHOLD_USD, isGuardEnabled } from "./guard.js";
import { callMcpTool, connectMcpClient, listAnthropicTools, type AnthropicTool } from "./mcpClient.js";
import { approveInvoiceTool, executeApproveInvoice } from "./tools/approveInvoice.js";

const MODEL = "claude-sonnet-4-5";
const MCP_TOOL_NAMES = ["get_invoice", "calculate_regional_tax", "request_human_approval"] as const;

const anthropic = new Anthropic();

function buildSystemPrompt(): string {
  return (
    "Sos un agente de aprobación de facturas. Se te da un invoiceId.\n" +
    "1. Consultá la factura con get_invoice para obtener su total.\n" +
    "2. Si hace falta, usá calculate_regional_tax para verificar la tasa de IVA aplicada.\n" +
    `3. Si el total es menor o igual a ${AUTO_APPROVAL_THRESHOLD_USD}, llamá a approve_invoice.\n` +
    `4. Si el total supera ${AUTO_APPROVAL_THRESHOLD_USD}, NUNCA llames a approve_invoice — ` +
    "llamá a request_human_approval en su lugar, con el motivo de la escalada.\n" +
    "Al final, respondé en texto plano cuál fue la decisión (aprobado automáticamente / escalado a revisión humana) y por qué."
  );
}

async function dispatchToolCall(
  mcpClient: Awaited<ReturnType<typeof connectMcpClient>>,
  toolUse: ToolUseBlock,
): Promise<ToolResultBlockParam> {
  const input = toolUse.input as Record<string, unknown>;

  if (toolUse.name === "approve_invoice") {
    const result = executeApproveInvoice(input as { invoiceId: string; total: number });
    return { type: "tool_result", tool_use_id: toolUse.id, content: result.text, is_error: result.isError };
  }

  const result = await callMcpTool(mcpClient, toolUse.name, input);
  return { type: "tool_result", tool_use_id: toolUse.id, content: result.text, is_error: result.isError };
}

export async function runInvoiceApprovalLoop(invoiceId: string): Promise<string> {
  const mcpClient = await connectMcpClient();
  const allMcpTools = await listAnthropicTools(mcpClient);
  const scopedMcpTools = allMcpTools.filter((tool) => (MCP_TOOL_NAMES as readonly string[]).includes(tool.name));
  const tools: AnthropicTool[] = [...scopedMcpTools, approveInvoiceTool];

  if (!isGuardEnabled()) {
    console.warn(
      "[guard] DISABLE_GUARD=true — el guard programático está desactivado. " +
        "Solo la instrucción del system prompt puede impedir una autoaprobación indebida.",
    );
  }

  const { finalText } = await runAgenticLoop({
    anthropic,
    model: MODEL,
    system: buildSystemPrompt(),
    tools,
    initialUserMessage: `Evaluá la factura ${invoiceId} y decidí si se autoaprueba o se escala.`,
    onToolUse: (iteration, blocks) => {
      for (const block of blocks) {
        console.log(`[loop] iteración ${iteration}: ejecutando tool "${block.name}"`, block.input);
      }
    },
    dispatchToolUseBlocks: async (blocks) => {
      const results: ToolResultBlockParam[] = [];
      for (const block of blocks) {
        results.push(await dispatchToolCall(mcpClient, block));
      }
      return results;
    },
  });

  return finalText;
}
