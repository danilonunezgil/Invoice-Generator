import { ApiError, categorize } from "./errors.js";

const BASE_URL = process.env.INVOICE_API_BASE_URL ?? "http://localhost:8080";

interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: body !== undefined ? { "Content-Type": "application/json" } : undefined,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : String(cause);
    throw new ApiError(0, "Connection error", message, "transient", true);
  }

  if (!response.ok) {
    let problem: ProblemDetail = {};
    const contentType = response.headers.get("content-type") ?? "";
    if (contentType.includes("json")) {
      problem = (await response.json().catch(() => ({}))) as ProblemDetail;
    }
    const { category, isRetryable } = categorize(response.status);
    throw new ApiError(
      response.status,
      problem.title ?? response.statusText,
      problem.detail ?? "Sin detalle adicional.",
      category,
      isRetryable,
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export async function get<T>(path: string): Promise<T> {
  return request<T>("GET", path);
}

export async function post<T>(path: string, body?: unknown): Promise<T> {
  return request<T>("POST", path, body);
}

/** Descarga binaria (PDFs) — no pasa por el parseo JSON de `request`. */
export async function getBinary(path: string): Promise<{ base64: string; contentType: string }> {
  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`);
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : String(cause);
    throw new ApiError(0, "Connection error", message, "transient", true);
  }
  if (!response.ok) {
    const { category, isRetryable } = categorize(response.status);
    const text = await response.text().catch(() => "");
    throw new ApiError(response.status, response.statusText, text || "Sin detalle adicional.", category, isRetryable);
  }
  const buffer = Buffer.from(await response.arrayBuffer());
  return {
    base64: buffer.toString("base64"),
    contentType: response.headers.get("content-type") ?? "application/pdf",
  };
}
