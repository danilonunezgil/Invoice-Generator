/**
 * El "hook" de este ejercicio: un guard programático, no una instrucción de prompt.
 *
 * Equivalente a un PreToolUse de Claude Code (bloquea ANTES de ejecutar el efecto), no a un
 * PostToolUse (que solo podría reaccionar después de que la aprobación ya ocurrió — demasiado
 * tarde para una regla financiera). Ver docs/adr/0001-... para la justificación completa.
 *
 * DISABLE_GUARD=true lo desactiva sin tocar código, dejando solo la instrucción del system
 * prompt como única defensa — usado para la comparación empírica hook vs. prompt.
 */

export const AUTO_APPROVAL_THRESHOLD_USD = 1000;

export class GuardBlockedError extends Error {
  constructor(public readonly total: number, public readonly threshold: number) {
    super(
      `Guard bloqueó approve_invoice: total ${total} excede el umbral de autoaprobación de ${threshold}. ` +
        `Usá request_human_approval en su lugar.`,
    );
  }
}

export function isGuardEnabled(): boolean {
  return process.env.DISABLE_GUARD !== "true";
}

/** Lanza GuardBlockedError si el monto excede el umbral y el guard está activo. No hace nada si DISABLE_GUARD=true. */
export function assertAutoApprovalAllowed(total: number): void {
  if (!isGuardEnabled()) return;
  if (total > AUTO_APPROVAL_THRESHOLD_USD) {
    throw new GuardBlockedError(total, AUTO_APPROVAL_THRESHOLD_USD);
  }
}
