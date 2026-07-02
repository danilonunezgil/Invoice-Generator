# Invoice Generation — Project Context

## Stack
- Java 17, Spring Boot 3.5.16
- PostgreSQL + Flyway (migraciones en src/main/resources/db/migration, formato V{n}__descripcion.sql)
- JasperReports para PDF (plantillas .jrxml en src/main/resources/reports/)
- Testcontainers para tests de integración (nunca usar H2 para tests de repositorio)

## Arquitectura
- domain/: entidades JPA y lógica de negocio pura, sin dependencias de Spring
- application/: servicios de casos de uso (orquestan domain + infrastructure)
- infrastructure/: adaptadores (repos JPA, generador Jasper, clientes externos)
- api/: controladores REST, DTOs de entrada/salida

## Convenciones
- Toda entidad nueva requiere: migración Flyway + repositorio + test de integración
- Nunca lanzar excepciones genéricas: usar excepciones de dominio (InvoiceNotFoundException, etc.)
- Los DTOs de API nunca exponen entidades JPA directamente

## Comandos
- Tests: `./mvnw test`
- Tests de integración: `./mvnw verify -Pintegration`
- Levantar Postgres local: `docker compose up -d db`
- Correr app: `./mvnw spring-boot:run`

## Reglas de negocio del dominio factura
- Numeración de factura: formato INV-{año}-{secuencial}, único por año fiscal
- Una factura en estado PAID no puede modificarse
- IVA por defecto 21%, pero es configurable por TaxRule según región del cliente