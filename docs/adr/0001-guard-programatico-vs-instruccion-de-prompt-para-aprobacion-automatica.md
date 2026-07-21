# 0001 — Guard programático vs. instrucción de prompt para la autoaprobación de facturas

## Status

Accepted

## Context

`agents/invoice-approval-agent/` decide automáticamente si una factura se autoaprueba o se
escala a revisión humana, según un umbral de monto (`AUTO_APPROVAL_THRESHOLD_USD = 1000`,
ver `src/guard.ts`). Esta es exactamente el tipo de regla que el examen (Domain 1) pide poder
implementar de dos formas distintas:

1. **Instrucción de prompt**: decirle al modelo, en el system prompt, "nunca aprobés
   automáticamente una factura de más de $1000".
2. **Hook / guard programático**: un chequeo determinista, fuera del control del modelo, que
   impide la ejecución del efecto (aprobar) si el monto excede el umbral.

Este repo ya usa el mecanismo (2) en otro contexto, dentro de Claude Code mismo:
`.claude/hooks/protect-applied-migrations.py`, registrado como `PreToolUse` en
`.claude/settings.json`, bloquea con `permissionDecision: "deny"` cualquier edición a una
migración Flyway ya versionada — sin depender de que el modelo "se acuerde" de la regla. El
guard de este agente es el mismo principio, aplicado fuera de Claude Code, en un host propio
escrito a mano.

## Decision

`src/guard.ts` implementa `assertAutoApprovalAllowed(total)`, invocada desde el *executor* de
la tool local `approve_invoice` (`src/tools/approveInvoice.ts`) **antes** de producir el efecto
de "aprobado". Si el total supera el umbral, el guard lanza `GuardBlockedError`, que el loop
traduce en un `tool_result` con `is_error: true` — el modelo se entera de que fue bloqueado y
debe usar `request_human_approval`, pero nunca controla si el bloqueo ocurre.

Esto es deliberadamente un guard **tipo PreToolUse, no PostToolUse** — aunque el pedido
original de este ejercicio mencionaba "PostToolUse", corregimos el diseño: la corrección
importa para el examen. Un guard PostToolUse solo podría reaccionar *después* de que la tool ya
ejecutó su efecto — para una aprobación financiera automática, eso significa que la factura ya
quedaría "aprobada" antes de que cualquier lógica pueda revertirlo. El guard tiene que evaluar
la condición **antes** de que el efecto real ocurra, que es exactamente el momento en que
`executeApproveInvoice` llama a `assertAutoApprovalAllowed` — antes de construir la respuesta
de éxito.

`DISABLE_GUARD=true` desactiva el guard sin tocar código (mismo path, un flag de entorno),
dejando **solo** la instrucción del system prompt como defensa. Esto existe únicamente para la
comparación empírica de abajo — no es una configuración pensada para uso real.

## Consequences

- La regla de negocio ("nunca autoaprobar > $1000") queda garantizada a nivel de proceso,
  independientemente de cómo interprete el modelo el prompt, de cambios de fraseo, o de
  variabilidad entre corridas.
- El guard vive en el *executor* de la tool (capa de la aplicación), no en el mcp-server: las
  tools de solo lectura (`get_invoice`, `calculate_regional_tax`) y `request_human_approval` no
  necesitan guard — ya son inherentemente seguras o ya son la vía de escalamiento. Solo
  `approve_invoice`, la única acción con consecuencia real de este agente, lo requiere.
- Este guard es local a este agente standalone — no pasa por el motor de permisos de Claude
  Code (`.claude/settings.json`). Si estas mismas tools se usan interactivamente desde una
  sesión de Claude Code, la defensa equivalente sería un hook `PreToolUse` real de Claude Code
  apuntando a `mcp__invoice-api__*`, análogo a `protect-applied-migrations.py`.
- Duplica, en cierta medida, la instrucción que igual se le da al modelo en el prompt (para que
  sepa qué hacer en el caso normal) — pero la fuente de verdad para el caso límite es el guard,
  no el prompt. El prompt sigue siendo necesario para la *ergonomía* del flujo (que el modelo
  elija la tool correcta la mayoría de las veces); el guard es la garantía para el caso en que
  no lo haga.

## Evidencia empírica

**Pendiente** — requiere `ANTHROPIC_API_KEY` real, que no estaba disponible en el entorno de
la sesión donde se implementó este agente. Para completar esta sección, correr:

```bash
cd agents/invoice-approval-agent
npm run build

# Caso normal (guard activo) — debería autoaprobar
node dist/index.js <invoiceId con total <= 1000>

# Caso normal (guard activo) — debería escalar, y el guard debería bloquear cualquier intento
# de approve_invoice si el modelo lo intentara igual
node dist/index.js <invoiceId con total > 1000>

# Comparación empírica: SOLO instrucción de prompt, sin guard, repetido varias veces sobre la
# factura de > $1000
for i in 1 2 3 4 5 6 7 8 9 10; do npm run demo:no-hook -- <invoiceId con total > 1000>; done
```

Completar esta tabla con los resultados reales:

| Corrida (`DISABLE_GUARD=true`) | Decisión del modelo | ¿Respetó la instrucción del prompt? |
|---|---|---|
| 1 | | |
| 2 | | |
| ... | | |

Conclusión a completar: de N corridas sin guard, ¿cuántas veces el modelo aprobó indebidamente
una factura de más de $1000 pese a la instrucción explícita en el prompt? Ese número es la
evidencia concreta de por qué un guard programático no es opcional para una regla con
consecuencias reales.
