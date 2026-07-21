# Invoice Generator

Servicio de facturación construido con Spring Boot 3.5.16 y Java 17. Expone una API REST para emitir facturas, calcular impuestos por región y generar el PDF de la factura con JasperReports.

---

## Índice

1. [Stack técnico](#stack-técnico)
2. [Arquitectura](#arquitectura)
3. [Estructura del proyecto](#estructura-del-proyecto)
4. [Modelo de dominio y reglas de negocio](#modelo-de-dominio-y-reglas-de-negocio)
5. [Persistencia](#persistencia)
6. [API REST](#api-rest)
7. [Servidor MCP](#servidor-mcp)
8. [Agente de aprobación de facturas: agentic loop manual](#agente-de-aprobación-de-facturas-agentic-loop-manual)
9. [Cómo correr el proyecto](#cómo-correr-el-proyecto)
10. [Testing](#testing)
11. [CI/CD: revisión automática de PRs con Claude Code](#cicd-revisión-automática-de-prs-con-claude-code)
12. [Configuración de Claude Code en este repo](#configuración-de-claude-code-en-este-repo)
13. [Guía de caso de uso completo](docs/guia-caso-de-uso.md)

---

## Stack técnico

| Capa | Tecnología |
|---|---|
| Lenguaje / runtime | Java 17 |
| Framework | Spring Boot 3.5.16 (Web, Data JPA, Validation, Actuator) |
| Base de datos | PostgreSQL |
| Migraciones | Flyway |
| Generación de PDF | JasperReports 6.21.3 (plantillas `.jrxml` compiladas en build time vía `jasperreports-plugin`) |
| Documentación de API | springdoc-openapi (Swagger UI) |
| Testing de integración | Testcontainers (Postgres real, sin H2) |
| Build | Maven (`mvnw`) |

---

## Arquitectura

El proyecto sigue **arquitectura hexagonal (ports & adapters)**, separada en cuatro paquetes con una regla de dependencia estricta: el dominio no conoce a Spring, y `infrastructure`/`api` dependen de `application`, nunca al revés.

```
┌─────────────┐      ┌──────────────────┐      ┌────────────────┐
│     api/     │ ───▶ │   application/   │ ◀─── │ infrastructure/ │
│ Controllers  │      │  Casos de uso +  │      │  Adaptadores:   │
│ + DTOs       │      │  interfaces      │      │  JPA repos,     │
│              │      │  (port/)         │      │  Jasper, etc.   │
└─────────────┘      └────────┬─────────┘      └────────────────┘
                               │ usa
                               ▼
                        ┌─────────────┐
                        │   domain/   │
                        │  Entidades  │
                        │  + reglas   │
                        └─────────────┘
```

- **`domain/`** — Entidades JPA (`Invoice`, `Customer`, `LineItem`, `TaxRule`, `Address`, `InvoiceNumber`) y lógica de negocio pura (transiciones de estado, cálculo de totales). Sin dependencias de Spring más allá de anotaciones JPA. Las excepciones de negocio viven en `domain/exception/`.
- **`application/`** — Casos de uso (`InvoiceService`) que orquestan el dominio. Define **puertos** (`application/port/`: `InvoiceRepository`, `CustomerRepository`, `TaxRuleRepository`, `InvoiceNumberGenerator`, `InvoicePdfGenerator`) como interfaces — el servicio no sabe si detrás hay JPA o Jasper.
- **`infrastructure/`** — Adaptadores que implementan los puertos: repos Spring Data JPA (`InvoiceJpaRepository`, etc.), los adaptadores que los envuelven (`JpaInvoiceRepositoryAdapter`, etc.) y `JasperInvoicePdfGenerator`, que compila los parámetros del `Invoice` y llama a JasperReports.
- **`api/`** — Controladores REST (`InvoiceController`) y DTOs de entrada/salida. Los DTOs nunca exponen las entidades JPA directamente. `GlobalExceptionHandler` traduce las excepciones de dominio a respuestas `ProblemDetail` (RFC 7807).

Este desacoplamiento es lo que permite, por ejemplo, testear `InvoiceService` contra un Postgres real sin arrancar Jasper, o cambiar el motor de PDF sin tocar el caso de uso.

---

## Estructura del proyecto

```
src/main/java/com/danno/invoice_generator/
├── InvoiceGeneratorApplication.java
├── api/
│   ├── InvoiceController.java        # Endpoints REST
│   ├── GlobalExceptionHandler.java    # Excepciones de dominio → ProblemDetail
│   └── dto/                           # Request/response records
├── application/
│   ├── InvoiceService.java            # Casos de uso
│   └── port/                          # Interfaces (puertos)
├── domain/
│   ├── Invoice.java, Customer.java, LineItem.java, TaxRule.java, Address.java
│   ├── InvoiceNumber.java, InvoiceStatus.java
│   └── exception/                     # Excepciones de negocio
└── infrastructure/
    ├── *JpaRepository.java            # Spring Data JPA
    ├── Jpa*Adapter.java                # Implementaciones de los puertos
    └── JasperInvoicePdfGenerator.java  # Adaptador de generación de PDF

src/main/resources/
├── application.yaml
├── db/migration/                      # Flyway, V1..V5
└── reports/                           # invoice.jrxml + sub_line_items.jrxml
```

---

## Modelo de dominio y reglas de negocio

**Ciclo de vida de una factura** (`InvoiceStatus`): `DRAFT → ISSUED → PAID`, con `CANCELLED` alcanzable desde `DRAFT` o `ISSUED`. Las transiciones inválidas lanzan `InvalidInvoiceStateException`.

- **Numeración**: al emitir (`issue`), se asigna un `InvoiceNumber` con formato `INV-{año}-{secuencial}`, único por año fiscal. Lo genera `InvoiceNumberGenerator` (puerto) / `JpaInvoiceNumberGenerator` (adaptador), respaldado por la tabla `invoice_sequence`.
- **Inmutabilidad**: una factura `PAID` o `CANCELLED` no admite `addLineItem`/`removeLineItem` (`InvoiceNotModifiableException`).
- **Impuestos**: IVA por defecto 21% (`TaxRule.DEFAULT_RATE`), pero configurable por región del cliente vía la tabla `tax_rules` (`resolveTaxRate` en `InvoiceService`).
- **Montos**: siempre `BigDecimal`, nunca `double`/`float`.
- **Optimistic locking**: `Invoice` tiene `@Version` para evitar sobrescrituras concurrentes.
- **PDF**: solo se puede generar sobre una factura ya emitida (requiere `InvoiceNumber` asignado); si no, `InvalidInvoiceStateException`.

---

## Persistencia

- Migraciones Flyway en `src/main/resources/db/migration`, formato `V{n}__descripcion.sql`. Hibernate corre en modo `validate` (`ddl-auto: validate`) — el esquema lo gobiernan las migraciones, no Hibernate.
- Tablas: `customers`, `tax_rules`, `invoices`, `line_items`, `invoice_sequence`.
- IDs siempre `UUID`, nunca `Long` autoincremental.
- `open-in-view: false` — por eso `InvoiceService` fuerza la carga de la colección `lineItems` dentro de la transacción antes de devolver la entidad a `api/`.

---

## API REST

Base path: `/api/invoices`

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/invoices` | Crea una factura en `DRAFT` |
| `POST` | `/api/invoices/{id}/line-items` | Agrega una línea (calcula impuesto según región del cliente) |
| `POST` | `/api/invoices/{id}/issue` | Emite la factura (asigna número, pasa a `ISSUED`) |
| `POST` | `/api/invoices/{id}/pay` | Marca como `PAID` |
| `POST` | `/api/invoices/{id}/cancel` | Cancela la factura |
| `GET` | `/api/invoices/{id}` | Consulta una factura |
| `GET` | `/api/invoices/{id}/pdf` | Descarga el PDF (`application/pdf`) |
| `GET` | `/api/invoices/overdue?customerId=` | Facturas `ISSUED` de un cliente con `dueDate` vencida |
| `GET` | `/api/tax-rules/{regionCode}` | Tasa de IVA resuelta para una región (con el default 21% si no hay `TaxRule`) |

### Swagger UI

Con la app corriendo: **http://localhost:8080/swagger-ui.html** (spec crudo en `/v3/api-docs`). Documentación autogenerada por springdoc a partir de `InvoiceController`, sin configuración adicional — permite ejecutar cada endpoint con "Try it out" en vez de usar Postman/curl.

---

## Servidor MCP

`mcp-server/` es un servidor **MCP (Model Context Protocol)** en TypeScript que expone esta misma API REST como *tools*, *resources* y un *prompt* para cualquier cliente MCP (Claude Code, Claude Desktop, etc.), en vez de requerir que el agente arme requests HTTP a mano. No sustituye a la API — es una capa de integración sobre ella.

### Tools (mapeados 1:1 a los endpoints REST)

| Tool | Endpoint | Descripción |
|---|---|---|
| `create_customer` | `POST /api/customers` | Da de alta un cliente |
| `get_customer` | `GET /api/customers/{id}` | Consulta un cliente |
| `create_invoice_draft` | `POST /api/invoices` | Crea una factura en `DRAFT` |
| `add_invoice_line_item` | `POST /api/invoices/{id}/line-items` | Agrega una línea |
| `issue_invoice` | `POST /api/invoices/{id}/issue` | Emite la factura |
| `pay_invoice` | `POST /api/invoices/{id}/pay` | Marca como `PAID` |
| `cancel_invoice` | `POST /api/invoices/{id}/cancel` | Cancela la factura |
| `get_invoice` | `GET /api/invoices/{id}` | Consulta una factura completa |
| `list_overdue_invoices` | `GET /api/invoices/overdue?customerId=` | Facturas vencidas de un cliente (riesgo crediticio) |
| `calculate_regional_tax` | `GET /api/tax-rules/{regionCode}` | Tasa de IVA para una región, sin necesidad de crear una factura |
| `request_human_approval` | simulada (no hay endpoint real) | Escala una decisión a revisión humana — persiste un registro en `mcp-server/data/pending-approvals.json` |
| `onboard_customer_and_invoice` | encadena los 4 primeros | Alta + primera factura en una sola llamada — equivalente tipado de la skill `onboard-customer-purchase` |

### Resources y prompt

- `invoice://{invoiceId}/pdf` y `report://billing-summary/pdf` — exponen los PDFs como contenido direccionable por URI (`GET /api/invoices/{id}/pdf` y `GET /api/reports/billing-summary/pdf` por detrás).
- `draft-invoice-for-customer` — prompt que guía el flujo alta-de-cliente + primera factura, invocando los tools en el orden correcto.

### Manejo de errores

Cada tool traduce el `ProblemDetail` (RFC 7807) de la API en un resultado MCP con `isError: true` más metadata estructurada: `errorCategory` (`validation` | `not_found` | `business` | `transient`) e `isRetryable`. Así el agente distingue, por ejemplo, un 409 por violar una regla de negocio (no reintentable) de un fallo de red (sí reintentable) — ver `mcp-server/src/errors.ts`.

### Cómo correr y registrar

```bash
cd mcp-server
npm install
npm run build                 # compila a dist/
```

Ya está registrado en `.mcp.json` (raíz del repo, scope `project`, versionado — sin secretos porque la API no tiene auth todavía). La primera vez que abrís el repo con Claude Code, hay que aprobarlo (`claude mcp list` lo muestra como "⏸ Pending approval" hasta que corras `claude` interactivamente y lo apruebes).

Para debuggear un tool suelto sin pasar por Claude Code:

```bash
npx @modelcontextprotocol/inspector node mcp-server/dist/index.js
```

---

## Agente de aprobación de facturas: agentic loop manual

`agents/invoice-approval-agent/` es un agente standalone en TypeScript, separado de `mcp-server/` y de Claude Code, escrito para repasar en código real el **Domain 1** (Agentic Loop, Hooks, Coordinador/Subagentes) de la certificación **Claude Code Architect Foundations**. Llama directo a la API de Claude (`@anthropic-ai/sdk`) y actúa como su propio **host MCP**: abre un `Client` + `StdioClientTransport` contra `mcp-server/` en vez de reimplementar llamadas HTTP a la API de facturación. No usa ningún framework de agentes — el loop está escrito a mano para que la anatomía completa quede visible.

### Anatomía del loop (`src/agenticLoopCore.ts`)

```
messages = [user: "Evaluá la factura {invoiceId}..."]
loop:
  response = anthropic.messages.create({ system, tools, messages })
  messages.push({ role: "assistant", content: response.content })

  switch response.stop_reason:
    "tool_use"   -> ejecutar cada bloque tool_use, empaquetar TODOS los tool_result en un
                    solo mensaje "user", volver a llamar
    "end_turn"   -> devolver la decisión final (fin del loop)
    "max_tokens" -> error explícito (respuesta truncada, no es una decisión completa)
    otro         -> error explícito ("stop_reason inesperado")
```

Un `MAX_ITERATIONS` actúa como *circuit breaker* de última instancia — nunca como la lógica que decide cuándo parar; si se dispara, es un bug, no una decisión legítima. Esta misma función se reutiliza sin cambios para el agente simple, cada subagente y el coordinador (ver abajo): lo único que varía por rol es qué tools se ofrecen y cómo se ejecutan, nunca el manejo de `stop_reason`.

**Anti-patrones que evita a propósito** (y por qué):

1. Parsear el texto de la respuesta buscando palabras ("aprobado") en vez de inspeccionar `stop_reason`/bloques `tool_use` — frágil ante cambios de fraseo, sin garantía estructural.
2. Tratar cualquier `stop_reason` distinto de `end_turn` como "terminado" — se saltean tool calls pendientes, se rompe el contrato del protocolo.
3. Un límite arbitrario de iteraciones como *la* lógica de corte — puede cortar una secuencia legítima de varias tools, o enmascarar un loop infinito real en vez de exponerlo.
4. No manejar `max_tokens` explícitamente — una respuesta truncada tratada como completa corrompe el parseo de la decisión final.

### El hook: guard programático, no instrucción de prompt

`src/guard.ts` bloquea la tool local `approve_invoice` (`src/tools/approveInvoice.ts`) **antes** de que produzca su efecto si el total de la factura supera `AUTO_APPROVAL_THRESHOLD_USD` (1000) — equivalente a un hook **PreToolUse**, no PostToolUse: bloquear *después* de aprobar ya sería tarde para una regla financiera. El modelo nunca decide si el bloqueo ocurre; `DISABLE_GUARD=true` lo desactiva sin tocar código, dejando solo la instrucción del prompt, para poder comparar empíricamente cuántas veces el modelo la respeta igual sin el guard. Justificación completa, incluida la comparación con el hook `PreToolUse` real que ya usa este mismo repo (`.claude/hooks/protect-applied-migrations.py`, ver [sección siguiente](#configuración-de-claude-code-en-este-repo)), en [`docs/adr/0001-guard-programatico-vs-instruccion-de-prompt-para-aprobacion-automatica.md`](docs/adr/0001-guard-programatico-vs-instruccion-de-prompt-para-aprobacion-automatica.md).

### Coordinador + subagentes (`src/coordinator.ts`)

Extiende el mismo loop a un patrón coordinador-subagente:

- **Contexto explícito.** El coordinador resuelve `customerId`/`regionCode` una vez (vía `get_invoice`/`get_customer`) y se los pasa a cada subagente como input de su tool — el subagente `credit-checker` (`get_invoice` + `list_overdue_invoices`) y el subagente `tax-validator` (`get_invoice` + `calculate_regional_tax`) nunca heredan el historial completo de la conversación del coordinador, cada uno arranca su propia conversación aislada.
- **Ejecución paralela real.** El system prompt del coordinador exige invocar `invoke_credit_checker` e `invoke_tax_validator` en el mismo turno (dos bloques `tool_use` en una sola respuesta); `dispatch.ts` los corre con `Promise.allSettled`, no secuencialmente, y loggea timestamps de inicio/fin de cada uno para poder verificar el solapamiento.
- **Salida estructurada garantizada por la API, no por prompt.** Cada subagente cierra su investigación con una llamada forzada (`tool_choice: { type: "tool", name: "report_finding" }`, ver `src/subagents/reportFinding.ts`) — el resultado `{ finding, confidence, evidence }` queda garantizado por el tool-use forzado, no por instrucción.
- **Fallos parciales explícitos.** `dispatch.ts` envuelve cada subagente en su propio timeout (`Promise.race` implícito vía `withTimeout`); si `credit-checker` no responde a tiempo (simulable con `SIMULATE_CREDIT_CHECKER_TIMEOUT_MS`), el coordinador recibe `{ failure_type: "timeout", partial_results: null }` como `tool_result` de error y su system prompt le exige seguir con el hallazgo de `tax-validator`, anotando el gap de cobertura en la síntesis final en vez de ignorarlo.

### Cómo correr

```bash
cd agents/invoice-approval-agent
npm install && npm run build
cp .env.example .env   # completar ANTHROPIC_API_KEY

node dist/index.js <invoiceId>          # Fase E: agente simple con guard
npm run demo:no-hook -- <invoiceId>     # comparación empírica: solo prompt, sin guard
node dist/coordinator.js <invoiceId>    # Fase F: coordinador + subagentes en paralelo
```

Ver `agents/invoice-approval-agent/README.md` para el detalle de cada script.

---

## Cómo correr el proyecto

```bash
# 1. Levantar Postgres local
docker compose up -d db

# 2. Correr la app
./mvnw spring-boot:run
```

La app queda en `http://localhost:8080`. No hay endpoint para crear `customers` — se insertan directo en la base para pruebas manuales.

---

## Testing

```bash
./mvnw test                    # tests unitarios
./mvnw verify -Pintegration    # tests de integración (Testcontainers + Postgres real)
```

- Tests unitarios: sufijo `Test.java` (ej. `InvoiceControllerTest`).
- Tests de integración: sufijo `IT.java` (ej. `InvoiceServiceIT`, `JpaInvoiceNumberGeneratorIT`), corridos por Failsafe en el profile `integration`. Nunca mockean el repositorio — usan Postgres real vía Testcontainers.
- Patrón de nombres: `given_when_then`.

---

## CI/CD: revisión automática de PRs con Claude Code

`.github/workflows/claude-review.yml` corre el CLI de Claude Code (`claude -p`) sobre cada Pull Request para detectar bugs y problemas de seguridad antes del merge, comentando directamente sobre las líneas del diff.

**Trigger:** `pull_request` (`opened`, `synchronize`, `reopened`). No corre en push directo a `main`.

**Pasos del job `review`:**

1. Checkout del head del PR (`fetch-depth: 0`, necesario para diffear contra la rama base).
2. Instala el CLI (`npm install -g @anthropic-ai/claude-code`).
3. Calcula el diff del PR contra la rama base, limitado a archivos `.java`.
4. Arma un prompt de revisión y lo corre con `claude -p --output-format json --json-schema .github/claude-review-schema.json`. El schema fuerza cada hallazgo a `{file, line, severity: "bug"|"security"|"style", description}`.
5. Filtra con `jq` para quedarse solo con `severity: bug` o `security` — el estilo se detecta pero se descarta, para reducir ruido.
6. Publica cada hallazgo como comentario inline en el PR vía `actions/github-script` (`pulls.createReviewComment`).

**Seguridad:** el step que corre `claude -p` usa `--allowedTools "Read,Grep,Glob"` — sin `Bash`, `Write` ni `Edit`. El contenido de un PR es potencialmente no confiable (podría incluir un intento de prompt injection en el diff), así que el agente corre en modo solo-lectura.

**Contexto de proyecto:** como el CLI corre dentro del repo ya checkouteado, carga automáticamente `.claude/CLAUDE.md` y las reglas scoped de `.claude/rules/` (ver [sección siguiente](#configuración-de-claude-code-en-este-repo)) — el prompt del workflow no necesita repetir las convenciones de dominio, Claude ya las conoce por el contexto del propio repo.

**La API key:** el workflow usa el secret de repo `ANTHROPIC_API_KEY` (Settings → Secrets and variables → Actions), inyectado como variable de entorno solo en el step que invoca `claude`. Nunca está hardcodeada en el YAML. Es una API key de **console.anthropic.com** (facturación pay-as-you-go por token) — un plan **Claude Pro** de claude.ai no cubre este uso, son productos de facturación separada. `GITHUB_TOKEN` (usado para postear los comentarios) lo provee GitHub automáticamente por job, sin configuración manual.

---

## Configuración de Claude Code en este repo

Esta sección documenta cómo está configurado Claude Code para este proyecto — útil como referencia de estudio para la certificación **Claude Code Architect Foundations**. Cada mecanismo ilustra una forma distinta de inyectar contexto o extender el comportamiento del agente.

### 1. `.claude/CLAUDE.md` — Memoria de proyecto

Archivo de contexto persistente que se carga automáticamente en **cada** conversación dentro de este repo. Documenta stack, arquitectura de capas y convenciones (numeración de facturas, IVA, estados inmutables). A diferencia de una memoria de usuario (que vive fuera del repo y persiste entre proyectos), `CLAUDE.md` es memoria **de proyecto**: versionada en git, compartida por todo el equipo, y el mecanismo recomendado para instrucciones que aplican siempre, sin importar qué archivo se esté tocando.

### 2. `.claude/rules/*.md` — Reglas con scope por path

A diferencia de `CLAUDE.md` (siempre activo), las reglas en `.claude/rules/` se activan **condicionalmente** según el frontmatter `paths`, que define un glob. Solo se inyectan en el contexto cuando Claude está trabajando sobre un archivo que hace match — así se evita cargar reglas irrelevantes para cada tarea.

| Regla | `paths` | Contenido |
|---|---|---|
| `jpa-entities.md` | `**/domain/**` | `@Version` para optimistic locking, IDs UUID, `BigDecimal` para montos |
| `tests.md` | `**/*Test.java`, `**/*IT.java` | Patrón `given_when_then`, separación Test/IT, Testcontainers obligatorio en IT |
| `jasper-reports.md` | `**/reports/**`, `**/*.jrxml` | Compilación en build time, convención de getters para `JRBeanCollectionDataSource`, prefijo `sub_` en subreports |

Esto es lo que en la arquitectura de Claude Code se conoce como **contexto scoped** — reglas que escalan sin inflar cada prompt con instrucciones de dominios que no aplican a la tarea actual.

### 3. `.claude/commands/*.md` — Slash commands personalizados

`new-entity.md` define el comando `/new-entity`, invocado como `/new-entity NombreEntidad`. Estructura clave:

- Frontmatter `argument-hint: [entity-name]` — describe el argumento esperado, se muestra como ayuda al autocompletar.
- `$ARGUMENTS` — placeholder que se sustituye por lo que el usuario escriba después del comando.
- El cuerpo del comando referencia explícitamente `.claude/rules/jpa-entities.md`, encadenando el slash command con las reglas scoped — así el resultado (entidad + repo + migración Flyway + test IT) sigue las convenciones del proyecto sin repetirlas en el prompt.

Esto ejemplifica cómo los slash commands funcionan como **prompts parametrizados y reutilizables**, distintos de las reglas (que son pasivas/contextuales) y de `CLAUDE.md` (que es siempre-activo y no parametrizado).

### 4. MCP — servidor personalizado + servidor existente

Ver la sección [Servidor MCP](#servidor-mcp) para el detalle de tools/resources/prompt. Acá lo relevante es cómo encaja en la configuración de Claude Code:

- **Dos servidores, dos scopes.** `invoice-api` (el servidor personalizado de este repo) vive en `.mcp.json` en scope **project** — versionado, sin secretos, compartido con todo el equipo. `postgres-invoice` (el servidor MCP oficial de Postgres, apuntando a la base de este proyecto) se registró en scope **local** (`claude mcp add --scope local`) porque su connection string lleva usuario/contraseña — scope local no se versiona.
- **Conectar un MCP existente vs. construir uno propio.** `postgres-invoice` es un servidor de la comunidad (`@modelcontextprotocol/server-postgres`) reutilizado tal cual — expone un único tool `query`, de **solo lectura por diseño** (un `DELETE` falla a nivel de transacción de Postgres, no solo por permisos de Claude Code). `invoice-api` se construyó a medida porque envuelve reglas de negocio específicas de este dominio (numeración fiscal, transiciones de estado) que ningún servidor genérico expone.
- **Permisos por tool, no por servidor.** `.claude/settings.json` distingue tools de solo lectura (`get_invoice`, `get_customer`, el `query` de Postgres → `allow`) de tools que mutan estado (`create_*`, `issue_invoice`, `pay_invoice`, `cancel_invoice` → `ask`, piden confirmación humana en cada llamada). Es el mismo principio de *least privilege* que `--allowedTools "Read,Grep,Glob"` en `claude-review.yml` (sección anterior), aplicado en otra capa: ahí acota qué herramientas puede usar la CLI en CI sin supervisión; acá acota qué *tool calls* de MCP requieren aprobación humana en una sesión interactiva.
- **Aprobación de servidores de proyecto.** A diferencia de `CLAUDE.md` o las reglas (que se cargan sin pedir nada), un `.mcp.json` nuevo requiere aprobación explícita la primera vez que se abre el repo — un gate de seguridad para que un servidor de proyecto no se ejecute sin que un humano lo revise primero.

### 5. Hooks — guard programático real de Claude Code

`.claude/settings.json` registra dos hooks `PreToolUse`, cada uno un script Python en `.claude/hooks/`:

- `protect-applied-migrations.py` (`matcher: "Edit|Write"`) — bloquea editar un archivo de migración Flyway (`src/main/resources/db/migration/*.sql`) si ya está commiteado en git (`git ls-files --error-unmatch`). Flyway no permite modificar una migración ya aplicada; el hook lo hace cumplir *antes* de que la edición ocurra, devolviendo `{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "deny", ...}}` por stdout — el propio harness de Claude Code lee ese JSON y cancela la tool call.
- `protect-confidential-reference.py` (`matcher: "Bash"`) — protege el material confidencial de `docs/reference/` (ver `.claude/rules/claude-code-architect-guide.md`) de comandos Bash que intenten leerlo o copiarlo fuera del árbol de trabajo.

Esto es la misma idea que el guard de `agents/invoice-approval-agent/src/guard.ts` (ver [sección anterior](#agente-de-aprobación-de-facturas-agentic-loop-manual)), aplicada en dos lugares distintos:

| | Hook de Claude Code | Guard del agente standalone |
|---|---|---|
| Dónde vive | `.claude/hooks/*.py`, registrado en `settings.json` | Código propio del agente (`guard.ts`) |
| Quién lo ejecuta | El harness de Claude Code, antes de correr la tool | El *executor* de la tool local, antes de producir su efecto |
| Formato de bloqueo | JSON por stdout (`permissionDecision: "deny"`) | Excepción tipada (`GuardBlockedError`) capturada por el loop |
| Por qué existe | Reforzar una regla determinista sin depender de que el modelo la recuerde | Igual — ver [ADR 0001](docs/adr/0001-guard-programatico-vs-instruccion-de-prompt-para-aprobacion-automatica.md) |

Ambos son la misma respuesta a la misma pregunta del examen: "¿instrucción de prompt o guard programático?" — la diferencia es únicamente si el agente corre dentro de Claude Code (hook nativo) o fuera (guard propio, porque el motor de hooks de Claude Code no aplica a un host escrito a mano).

### Resumen de mecanismos

| Mecanismo | Se activa | Alcance | Parametrizable |
|---|---|---|---|
| `CLAUDE.md` | Siempre | Todo el repo | No |
| `rules/*.md` | Cuando el path activo hace match con el glob | Scoped por archivo/carpeta | No (pero sí condicional) |
| `commands/*.md` | Invocación explícita (`/nombre-comando`) | Bajo demanda | Sí, vía `$ARGUMENTS` |
| `.mcp.json` / MCP servers | Al conectar la sesión (previa aprobación si es scope project) | Tools/resources/prompts descubribles por el agente | Sí, vía el input schema de cada tool |
| `hooks` (`PreToolUse`) | Antes de cada tool call que matchea el `matcher` | Determinista, corre siempre que matchea (no depende del modelo) | Sí, vía el JSON que recibe por stdin (`tool_input`) |
