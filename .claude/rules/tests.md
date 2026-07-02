---
paths: ["**/*Test.java", "**/*IT.java"]
---
# Convenciones de tests
- Nombrar tests con patrón given_when_then
- Tests de integración terminan en IT.java, no en Test.java (para separarlos de tests unitarios)
- Usar @Testcontainers, nunca mockear el repositorio en tests IT