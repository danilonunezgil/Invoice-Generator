# invoice-approval-agent

Agente standalone de aprobación de facturas, escrito para repasar el **Domain 1** (Agentic Loop, Hooks, Coordinador/Subagentes) del examen **Claude Code Architect Foundations**.

No usa ningún framework de agentes: el loop `stop_reason` → ejecutar tool → volver a llamar está escrito a mano en [`src/loop.ts`](src/loop.ts) contra `@anthropic-ai/sdk`. Este agente es su propio **host MCP**: abre un `Client` + `StdioClientTransport` (ver [`src/mcpClient.ts`](src/mcpClient.ts)) contra el `mcp-server/` del repo, en vez de reimplementar llamadas HTTP a la API de facturación.

## Fase E — Agentic loop manual + hook

### Anatomía del loop

```
messages = [user: "Evaluá la factura {invoiceId}..."]
loop:
  response = anthropic.messages.create({ system, tools, messages })
  messages.push({ role: "assistant", content: response.content })

  switch response.stop_reason:
    "tool_use"   -> ejecutar cada bloque tool_use (get_invoice / calculate_regional_tax /
                    request_human_approval vía MCP, o approve_invoice vía el executor local
                    con guard), empaquetar TODOS los tool_result en un solo mensaje "user",
                    volver a llamar
    "end_turn"   -> devolver la decisión final (fin del loop)
    "max_tokens" -> error explícito (truncado, no es una decisión completa)
    otro         -> error explícito ("stop_reason inesperado")
```

Un contador `MAX_ITERATIONS` actúa como *circuit breaker* de última instancia (nunca como la lógica que decide cuándo parar — si se dispara, es un bug, no una decisión legítima).

### Anti-patrones que este loop evita a propósito

1. **Parsear el texto de la respuesta** buscando palabras ("aprobado") en vez de inspeccionar `stop_reason`/bloques `tool_use` — frágil ante cambios de fraseo, sin garantía estructural.
2. **Tratar cualquier `stop_reason` distinto de `end_turn` como "terminado"** — se saltean tool calls pendientes y se rompe el contrato del protocolo.
3. **Límite arbitrario de iteraciones como la lógica de corte** (ej. "correr 3 veces y parar") — puede cortar una secuencia legítima de varias tools, o enmascarar un loop infinito real en vez de exponerlo.
4. **No manejar `max_tokens` explícitamente** — tratar una respuesta truncada como completa corrompe el parseo de la decisión final.

### El hook: guard programático (no instrucción de prompt)

[`src/guard.ts`](src/guard.ts) intercepta `approve_invoice` **antes** de que corra su efecto — equivalente a un hook **PreToolUse**, no PostToolUse (bloquear *después* de aprobar ya sería tarde). Si el total supera `AUTO_APPROVAL_THRESHOLD_USD` (1000), el guard bloquea la llamada real y devuelve al modelo un `tool_result` de error indicando que debe escalar. El modelo nunca decide si el bloqueo ocurre.

`DISABLE_GUARD=true` desactiva el guard sin tocar código — deja solo la instrucción del system prompt como defensa, para la comparación empírica de abajo. Ver [`docs/adr/0001-...md`](../../docs/adr/0001-guard-programatico-vs-instruccion-de-prompt-para-aprobacion-automatica.md) para la justificación completa y los resultados de esa comparación.

## Uso

```bash
npm install
npm run build

# API Java corriendo en localhost:8080 y mcp-server/ compilado (npm --prefix ../../mcp-server run build)
node dist/index.js <invoiceId-de-una-factura-ISSUED>
```

- Factura con total ≤ $1000 → debería autoaprobarse (`approve_invoice`).
- Factura con total > $1000 → debería escalar (`request_human_approval`), y `mcp-server/data/pending-approvals.json` debería tener un registro nuevo.

### Comparación empírica: hook vs. instrucción de prompt

```bash
npm run demo:no-hook -- <invoiceId-de-mas-de-1000>
```

Correr esto varias veces (5-10) sobre la misma factura de más de $1000 y anotar cuántas veces el modelo igual respeta la instrucción del prompt vs. cuántas la ignora y llama `approve_invoice` directamente. Esos resultados van al ADR.

## Fase F — Coordinador + subagentes

```bash
node dist/coordinator.js <invoiceId>
```

Ver la sección correspondiente del ADR y los comentarios en `src/coordinator.ts`, `src/subagents/*.ts` y `src/dispatch.ts` para el patrón de ejecución paralela, contexto explícito por subagente, salida estructurada forzada (`tool_choice`) y manejo de timeout parcial.
