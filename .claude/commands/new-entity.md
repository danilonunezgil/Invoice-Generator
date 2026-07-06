---
argument-hint: [entity-name]
description: Crea una entidad de dominio nueva (clase en domain/, repositorio Spring Data, migración Flyway y test de integración IT) siguiendo las convenciones del proyecto. Úsalo cuando pidan agregar una entidad como Payment, Customer o similar.
---
Crea una nueva entidad de dominio llamada $ARGUMENTS siguiendo las convenciones de .claude/rules/jpa-entities.md. Debe incluir:
1. La clase entidad en domain/
2. Un repositorio Spring Data en infrastructure/
3. Una migración Flyway en db/migration/
4. Un test de integración IT.java