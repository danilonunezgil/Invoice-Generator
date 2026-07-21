import Anthropic from "@anthropic-ai/sdk";
import type { MessageParam, ToolUseBlock } from "@anthropic-ai/sdk/resources/messages";
import { reportFindingTool, structuredFindingSchema, type StructuredFinding } from "./types.js";

/**
 * Fuerza al subagente a devolver su hallazgo en el shape estructurado, usando
 * `tool_choice: { type: "tool", name: "report_finding" }` — la API garantiza que la respuesta
 * contenga ese tool_use con ese schema, en vez de confiar en que el modelo "se porte bien" y
 * devuelva JSON válido en texto libre.
 */
export async function forceStructuredFinding(
  anthropic: Anthropic,
  model: string,
  system: string,
  priorMessages: MessageParam[],
): Promise<StructuredFinding> {
  const messages: MessageParam[] = [
    ...priorMessages,
    { role: "user", content: "Reportá tu hallazgo final ahora usando report_finding." },
  ];

  const response = await anthropic.messages.create({
    model,
    max_tokens: 512,
    system,
    tools: [reportFindingTool] as Anthropic.Tool[],
    tool_choice: { type: "tool", name: "report_finding" },
    messages,
  });

  const toolUse = response.content.find(
    (block): block is ToolUseBlock => block.type === "tool_use" && block.name === "report_finding",
  );
  if (!toolUse) {
    throw new Error(
      "tool_choice forzó report_finding pero la respuesta no contiene ese tool_use — no debería pasar nunca.",
    );
  }
  return structuredFindingSchema.parse(toolUse.input);
}
