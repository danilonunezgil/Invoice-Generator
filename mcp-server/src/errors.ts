export type ErrorCategory = "validation" | "not_found" | "business" | "transient";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly title: string,
    public readonly detail: string,
    public readonly category: ErrorCategory,
    public readonly isRetryable: boolean,
  ) {
    super(`${title} (HTTP ${status}): ${detail}`);
  }
}

/**
 * 404 -> recurso referenciado no existe (validation, no transient: reintentar no ayuda).
 * 409 -> violación de una regla de negocio (estado de factura, numeración duplicada).
 * 400 -> entrada mal formada.
 * Cualquier otro fallo de red/servidor se trata como transient (sí vale la pena reintentar).
 */
export function categorize(status: number): { category: ErrorCategory; isRetryable: boolean } {
  if (status === 404) return { category: "not_found", isRetryable: false };
  if (status === 409) return { category: "business", isRetryable: false };
  if (status === 400) return { category: "validation", isRetryable: false };
  return { category: "transient", isRetryable: true };
}

export function toToolErrorResult(error: unknown) {
  if (error instanceof ApiError) {
    return {
      isError: true as const,
      content: [
        { type: "text" as const, text: error.message },
        {
          type: "text" as const,
          text: JSON.stringify({
            errorCategory: error.category,
            isRetryable: error.isRetryable,
            status: error.status,
            detail: error.detail,
          }),
        },
      ],
    };
  }
  const message = error instanceof Error ? error.message : String(error);
  return {
    isError: true as const,
    content: [
      { type: "text" as const, text: `Fallo inesperado llamando a la API: ${message}` },
      {
        type: "text" as const,
        text: JSON.stringify({ errorCategory: "transient", isRetryable: true, detail: message }),
      },
    ],
  };
}
