---
argument-hint: [entity-name]
---
Crea una nueva entidad de dominio llamada $ARGUMENTS siguiendo las convenciones de .claude/rules/jpa-entities.md. Debe incluir:
1. La clase entidad en domain/
2. Un repositorio Spring Data en infrastructure/
3. Una migración Flyway en db/migration/
4. Un test de integración IT.java