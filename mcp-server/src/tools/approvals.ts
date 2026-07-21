import { randomUUID } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { z } from "zod";
import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

interface PendingApproval {
  approvalId: string;
  invoiceId: string;
  amount: number;
  reason: string;
  status: "pending_human_review";
  requestedAt: string;
}

// Este archivo vive en mcp-server/{src,dist}/tools/approvals.ts|js — dos niveles arriba
// siempre resuelve a mcp-server/, sea que se corra compilado (dist/) o con tsx (src/).
const DATA_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..", "data");
const DATA_FILE = path.join(DATA_DIR, "pending-approvals.json");

async function readPendingApprovals(): Promise<PendingApproval[]> {
  try {
    const raw = await readFile(DATA_FILE, "utf-8");
    return JSON.parse(raw) as PendingApproval[];
  } catch {
    return [];
  }
}

async function appendPendingApproval(entry: PendingApproval): Promise<void> {
  await mkdir(DATA_DIR, { recursive: true });
  const existing = await readPendingApprovals();
  existing.push(entry);
  await writeFile(DATA_FILE, JSON.stringify(existing, null, 2), "utf-8");
}

export function registerApprovalTools(server: McpServer) {
  server.registerTool(
    "request_human_approval",
    {
      title: "Escalar a revisión humana",
      description:
        "Simula la escalada de una factura a un humano para aprobación manual (no hay dominio de aprobaciones en la API real: " +
        "esta tool solo persiste un registro local en mcp-server/data/pending-approvals.json). " +
        "Usar cuando el monto de la factura excede el umbral de autoaprobación, en vez de aprobarla automáticamente.",
      inputSchema: {
        invoiceId: z.string().uuid(),
        amount: z.number().describe("Monto total de la factura que motiva la escalada"),
        reason: z.string().describe("Motivo de la escalada (ej. 'total excede el umbral de autoaprobación de $1000')"),
      },
    },
    async ({ invoiceId, amount, reason }) => {
      const approvalId = randomUUID();
      const entry: PendingApproval = {
        approvalId,
        invoiceId,
        amount,
        reason,
        status: "pending_human_review",
        requestedAt: new Date().toISOString(),
      };
      await appendPendingApproval(entry);
      return { content: [{ type: "text" as const, text: JSON.stringify(entry) }] };
    },
  );
}
