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
7. [Cómo correr el proyecto](#cómo-correr-el-proyecto)
8. [Testing](#testing)
9. [CI/CD: revisión automática de PRs con Claude Code](#cicd-revisión-automática-de-prs-con-claude-code)
10. [Configuración de Claude Code en este repo](#configuración-de-claude-code-en-este-repo)
11. [Guía de caso de uso completo](docs/guia-caso-de-uso.md)

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

### Swagger UI

Con la app corriendo: **http://localhost:8080/swagger-ui.html** (spec crudo en `/v3/api-docs`). Documentación autogenerada por springdoc a partir de `InvoiceController`, sin configuración adicional — permite ejecutar cada endpoint con "Try it out" en vez de usar Postman/curl.

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

### Resumen de mecanismos

| Mecanismo | Se activa | Alcance | Parametrizable |
|---|---|---|---|
| `CLAUDE.md` | Siempre | Todo el repo | No |
| `rules/*.md` | Cuando el path activo hace match con el glob | Scoped por archivo/carpeta | No (pero sí condicional) |
| `commands/*.md` | Invocación explícita (`/nombre-comando`) | Bajo demanda | Sí, vía `$ARGUMENTS` |
