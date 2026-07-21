import "dotenv/config";
import path from "node:path";
import { fileURLToPath } from "node:url";
import Anthropic from "@anthropic-ai/sdk";
import type { ToolResultBlockParam, ToolUseBlock } from "@anthropic-ai/sdk/resources/messages";
import { runAgenticLoop } from "./agenticLoopCore.js";
import { callMcpTool, connectMcpClient, type AnthropicTool } from "./mcpClient.js";
import { runCreditChecker, runTaxValidator } from "./dispatch.js";
import type { SubagentOutcome } from "./subagents/types.js";

const MODEL = "claude-sonnet-4-5";

/**
 * Tools LOCALES del coordinador: no pasan por el mcp-server, cada una dispara un subagente con
 * su propia conversación Claude aislada (ver dispatch.ts + subagents/*.ts). El input de cada una
 * es exactamente el contexto explícito que ese subagente recibe — nunca el historial completo
 * de esta conversación.
 */
const invokeCreditCheckerTool: AnthropicTool = {
  name: "invoke_credit_checker",
  description:
    "Invoca al subagente credit-checker: evalúa el historial crediticio (facturas vencidas) de UN cliente. " +
    "Recibe solo el customerId — el subagente no ve el resto de esta conversación.",
  input_schema: {
    type: "object",
    properties: { customerId: { type: "string", format: "uuid" } },
    required: ["customerId"],
  },
};

const invokeTaxValidatorTool: AnthropicTool = {
  name: "invoke_tax_validator",
  description:
    "Invoca al subagente tax-validator: verifica que la tasa de IVA aplicada a UNA factura sea correcta " +
    "para su región. Recibe solo invoiceId y regionCode — el subagente no ve el resto de esta conversación.",
  input_schema: {
    type: "object",
    properties: {
      invoiceId: { type: "string", format: "uuid" },
      regionCode: { type: "string" },
    },
    required: ["invoiceId", "regionCode"],
  },
};

const SYSTEM_PROMPT =
  "Sos el coordinador de aprobación de facturas. Tenés dos subagentes disponibles como tools:\n" +
  "- invoke_credit_checker(customerId): chequea historial crediticio del cliente.\n" +
  "- invoke_tax_validator(invoiceId, regionCode): valida la tasa de IVA aplicada.\n\n" +
  "Invocá AMBOS subagentes EN EL MISMO TURNO (dos bloques tool_use en una sola respuesta) — " +
  "nunca uno esperando el resultado del otro para recién ahí pedirlo.\n" +
  "Cuando tengas los dos resultados (o el error estructurado de alguno), sintetizá una decisión " +
  "final que cite explícitamente qué hallazgo vino de credit-checker y cuál de tax-validator " +
  "(nunca los mezcles sin atribución). Si algún subagente falló (failure_type en su resultado), " +
  "decilo explícitamente y aclará que la decisión se toma con cobertura parcial — nunca lo ignores en silencio.";

async function dispatchSubagentToolUse(
  block: ToolUseBlock,
): Promise<{ result: ToolResultBlockParam; outcome: SubagentOutcome }> {
  let outcome: SubagentOutcome;
  if (block.name === "invoke_credit_checker") {
    const { customerId } = block.input as { customerId: string };
    outcome = await runCreditChecker({ customerId });
  } else if (block.name === "invoke_tax_validator") {
    const { invoiceId, regionCode } = block.input as { invoiceId: string; regionCode: string };
    outcome = await runTaxValidator({ invoiceId, regionCode });
  } else {
    throw new Error(`Tool desconocida para el coordinador: ${block.name}`);
  }

  const content = outcome.ok
    ? { source: outcome.source, finding: outcome.finding }
    : { source: outcome.source, ...outcome.error };

  return {
    outcome,
    result: {
      type: "tool_result",
      tool_use_id: block.id,
      content: JSON.stringify(content),
      is_error: !outcome.ok,
    },
  };
}

export async function runCoordinator(invoiceId: string): Promise<string> {
  const anthropic = new Anthropic();
  const mcpClient = await connectMcpClient();

  const invoiceResult = await callMcpTool(mcpClient, "get_invoice", { invoiceId });
  const invoice = JSON.parse(invoiceResult.text) as { customerId: string };
  const customerResult = await callMcpTool(mcpClient, "get_customer", { customerId: invoice.customerId });
  const customer = JSON.parse(customerResult.text) as { regionCode: string };

  const { finalText } = await runAgenticLoop({
    anthropic,
    model: MODEL,
    system: SYSTEM_PROMPT,
    tools: [invokeCreditCheckerTool, invokeTaxValidatorTool],
    initialUserMessage:
      `Evaluá el riesgo de la factura ${invoiceId} del cliente ${invoice.customerId} (región ${customer.regionCode}). ` +
      `Para invoke_credit_checker usá customerId="${invoice.customerId}". ` +
      `Para invoke_tax_validator usá invoiceId="${invoiceId}" y regionCode="${customer.regionCode}".`,
    dispatchToolUseBlocks: async (blocks) => {
      // Ejecuta TODOS los tool_use de este turno en paralelo (Promise.allSettled): si el modelo
      // pidió credit-checker y tax-validator en la misma respuesta, corren solapados de verdad,
      // no uno tras otro. Cada uno atrapa sus propios errores (ver dispatch.ts), así que
      // allSettled acá es una defensa extra, no la vía normal de fallo.
      const dispatched = await Promise.allSettled(blocks.map((block) => dispatchSubagentToolUse(block)));
      const results: ToolResultBlockParam[] = [];
      for (let i = 0; i < dispatched.length; i++) {
        const settled = dispatched[i];
        const block = blocks[i];
        if (settled.status === "fulfilled") {
          const { result, outcome } = settled.value;
          const elapsedMs = Math.round(outcome.finishedAt - outcome.startedAt);
          console.log(
            `[coordinator] ${outcome.source}: start=${Math.round(outcome.startedAt)}ms ` +
              `end=${Math.round(outcome.finishedAt)}ms (${elapsedMs}ms) ok=${outcome.ok}`,
          );
          results.push(result);
        } else {
          console.error(`[coordinator] fallo inesperado despachando ${block.name}:`, settled.reason);
          results.push({
            type: "tool_result",
            tool_use_id: block.id,
            content: JSON.stringify({
              failure_type: "error",
              partial_results: null,
              message: String(settled.reason),
            }),
            is_error: true,
          });
        }
      }
      return results;
    },
  });

  return finalText;
}

const invoiceIdArg = process.argv[2];
const isMainModule = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMainModule) {
  if (!invoiceIdArg) {
    console.error("Uso: node dist/coordinator.js <invoiceId>");
    process.exit(1);
  }
  runCoordinator(invoiceIdArg)
    .then((decision) => {
      console.log("\n=== Decisión final (coordinador) ===");
      console.log(decision);
      process.exit(0);
    })
    .catch((error) => {
      console.error("\n=== Error ===");
      console.error(error instanceof Error ? error.message : error);
      process.exit(1);
    });
}
