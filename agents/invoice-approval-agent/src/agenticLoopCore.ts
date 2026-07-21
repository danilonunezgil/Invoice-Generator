import Anthropic from "@anthropic-ai/sdk";
import type { MessageParam, ToolResultBlockParam, ToolUseBlock } from "@anthropic-ai/sdk/resources/messages";
import type { AnthropicTool } from "./mcpClient.js";

// Salvavidas de última instancia: nunca es la lógica que decide cuándo parar (eso lo hace
// stop_reason), solo evita un loop infinito real si hay un bug. Si se dispara, es un bug.
const MAX_ITERATIONS = 15;

export interface AgenticLoopOptions {
  anthropic: Anthropic;
  model: string;
  system: string;
  tools: AnthropicTool[];
  initialUserMessage: string;
  dispatchToolUseBlocks: (blocks: ToolUseBlock[]) => Promise<ToolResultBlockParam[]>;
  onToolUse?: (iteration: number, blocks: ToolUseBlock[]) => void;
}

export interface AgenticLoopResult {
  finalText: string;
  messages: MessageParam[];
}

/**
 * El agentic loop manual explícito, factorizado UNA sola vez para no repetir el switch sobre
 * stop_reason en cada rol (agente simple, subagente, coordinador) — sigue siendo código propio
 * y visible, no un framework que oculte el ciclo. Lo que cambia entre roles es SOLO qué tools
 * se ofrecen y cómo se ejecutan (dispatchToolUseBlocks), nunca la lógica de stop_reason.
 */
export async function runAgenticLoop(options: AgenticLoopOptions): Promise<AgenticLoopResult> {
  const { anthropic, model, system, tools, initialUserMessage, dispatchToolUseBlocks, onToolUse } = options;
  const messages: MessageParam[] = [{ role: "user", content: initialUserMessage }];

  for (let iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
    const response = await anthropic.messages.create({
      model,
      max_tokens: 1024,
      system,
      tools: tools as Anthropic.Tool[],
      messages,
    });

    messages.push({ role: "assistant", content: response.content });

    switch (response.stop_reason) {
      case "tool_use": {
        const toolUseBlocks = response.content.filter(
          (block): block is ToolUseBlock => block.type === "tool_use",
        );
        onToolUse?.(iteration, toolUseBlocks);
        const toolResults = await dispatchToolUseBlocks(toolUseBlocks);
        messages.push({ role: "user", content: toolResults });
        continue;
      }

      case "end_turn": {
        const finalText = response.content
          .filter((block): block is Anthropic.TextBlock => block.type === "text")
          .map((block) => block.text)
          .join("\n");
        return { finalText, messages };
      }

      case "max_tokens":
        throw new Error(
          "stop_reason=max_tokens: la respuesta fue truncada antes de completarse. " +
            "No se puede tratar como una decisión final — subir max_tokens o acotar el prompt.",
        );

      default:
        throw new Error(`stop_reason inesperado: "${response.stop_reason}". No se sabe cómo continuar el loop.`);
    }
  }

  throw new Error(
    `Se alcanzó MAX_ITERATIONS (${MAX_ITERATIONS}) sin llegar a end_turn — probable bug de loop infinito, ` +
      "no una decisión legítima. Revisar el historial de mensajes.",
  );
}
