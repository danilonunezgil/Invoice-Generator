---
name: generate-jasper-template
description: Genera una plantilla JasperReports (.jrxml) nueva — reporte principal o subreport — siguiendo las convenciones del proyecto (compilación en build time vía maven-jasperreports-plugin, getters bean estándar para JRBeanCollectionDataSource, prefijo sub_ en subreports) y el generador Java que la consume. Úsalo cuando pidan un documento PDF nuevo (recibo, extracto de cliente, reporte de impuestos) o una tabla/sección nueva dentro de un reporte existente.
---

# Generar plantilla JasperReports

Sigue `.claude/rules/jasper-reports.md`. No inventes el XML desde cero: copia
las plantillas de `templates/` en este skill y completa los placeholders —
minimiza el riesgo de un `.jrxml` con schema inválido.

## 1. Identificar la fuente de datos

Antes de tocar XML, confirmá la clase Java que va a alimentar el reporte
(una entidad de `domain/` o un record/DTO de `infrastructure/`). Cada campo
que uses en el `.jrxml` necesita su getter estándar de JavaBean
(`getXxx()`/`isXxx()`) — `JRBeanCollectionDataSource` resuelve `$F{xxx}` por
convención de reflexión, sin getter no hay dato. Si falta, agregalo a la
clase antes de seguir.

## 2. Elegir tipo de plantilla y nombrarla

- **Reporte principal** (documento completo, ej. un extracto de cliente):
  nombre plano en minúscula, sin prefijo — ej. `customer-statement.jrxml`.
- **Subreport** (tabla/sección repetida dentro de otro reporte, como
  `sub_line_items.jrxml`): siempre prefijo `sub_`.

Un documento con una tabla de líneas normalmente necesita ambos: el
principal define `<parameter>` para los valores simples (cabecera) más un
`<parameter class="java.util.List">` para la colección, y un `<subreport>`
en su `<detail>` que la referencia; el subreport define un `<field>` por
columna de la tabla.

## 3. Copiar y completar la plantilla

Copiá el archivo correspondiente a `src/main/resources/reports/`:
- Reporte principal → `templates/main-report.jrxml`
- Subreport → `templates/subreport.jrxml`

Reemplazá `name="..."` por el nombre real y completá los `<parameter>` /
`<field>` según la fuente de datos del paso 1. Para inspirarte en bandas
(title/detail/summary, columnHeader+detail) mirá `invoice.jrxml` y
`sub_line_items.jrxml` — mismo `pageWidth`/`columnWidth`/márgenes ya
calibrados para A4.

## 4. Compilar (build time, no runtime)

No hay paso de compilación manual: `maven-jasperreports-plugin` ya está
configurado en `pom.xml` para tomar todo `.jrxml` bajo
`src/main/resources/reports/` y generar el `.jasper` correspondiente en
build time. Corré `./mvnw compile` y confirmá que no tira errores de XML —
así detectás un schema roto antes de llegar a runtime.

## 5. Conectar el generador Java

Seguí el patrón de `JasperInvoicePdfGenerator`
(`src/main/java/.../infrastructure/`): un `@Component` que implementa el
puerto de aplicación correspondiente (ver `application/port/`, o creá uno
nuevo si el documento es de un caso de uso distinto a facturación), carga
los `.jasper` compilados vía `ClassPathResource` + `JRLoader`, arma el
`Map<String, Object>` de parámetros (una entrada por `<parameter>`, más el
subreport compilado bajo la clave que uses en `$P{...}` para
`subreportExpression`), y llama `JasperFillManager.fillReport(...)` +
`JasperExportManager.exportReportToPdf(...)`. Si algo falla, lanzá la
excepción de dominio correspondiente (no una genérica) — mirá
`InvoicePdfGenerationException` como referencia.

## 6. Verificar

`./mvnw compile` para la generación del `.jasper`, y si hay un caso de uso
que ya invoca el generador, un test de integración (`*IT.java`,
`given_when_then`, sin mockear el repositorio) que efectivamente produzca
el PDF y valide su tamaño/contenido básico.
