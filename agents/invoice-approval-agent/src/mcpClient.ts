import path from "node:path";
import { fileURLToPath } from "node:url";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

/**
 * Este agente actúa como su propio host MCP (distinto de Claude Code): abre su propio
 * Client + StdioClientTransport contra el mismo mcp-server/ ya construido, en vez de
 * reimplementar las llamadas HTTP a la API de facturación.
 */

// repo-root/agents/invoice-approval-agent/{src,dist}/mcpClient.ts|js -> repo-root/mcp-server/dist/index.js
const MCP_SERVER_ENTRYPOINT = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "..",
  "..",
  "..",
  "mcp-server",
  "dist",
  "index.js",
);

export interface AnthropicTool {
  name: string;
  description?: string;
  input_schema: {
    type: "object";
    properties?: Record<string, unknown>;
    required?: string[];
    [key: string]: unknown;
  };
}

export interface McpToolResult {
  isError: boolean;
  text: string;
}

let client: Client | undefined;

export async function connectMcpClient(): Promise<Client> {
  if (client) return client;

  const transport = new StdioClientTransport({
    command: "node",
    args: [MCP_SERVER_ENTRYPOINT],
    env: {
      INVOICE_API_BASE_URL: process.env.INVOICE_API_BASE_URL ?? "http://localhost:8080",
    },
  });

  const newClient = new Client({ name: "invoice-approval-agent", version: "1.0.0" });
  await newClient.connect(transport);
  client = newClient;
  return client;
}

/** Descubre las tools del mcp-server y las convierte al shape `tools` de la Messages API. */
export async function listAnthropicTools(mcpClient: Client): Promise<AnthropicTool[]> {
  const { tools } = await mcpClient.listTools();
  return tools.map((tool) => ({
    name: tool.name,
    description: tool.description,
    input_schema: tool.inputSchema as AnthropicTool["input_schema"],
  }));
}

/** Ejecuta una tool MCP y aplana su resultado a texto plano + isError, para meterlo en un tool_result. */
export async function callMcpTool(
  mcpClient: Client,
  name: string,
  args: Record<string, unknown>,
): Promise<McpToolResult> {
  const result = await mcpClient.callTool({ name, arguments: args });
  const text = (result.content as Array<{ type: string; text?: string }>)
    .filter((block) => block.type === "text")
    .map((block) => block.text ?? "")
    .join("\n");
  return { isError: Boolean(result.isError), text };
}
