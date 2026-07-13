---
paths:
  - .claude/**
---
# Guía de referencia — Claude Code Architect Foundations

Estás editando configuración de Claude Code en este repositorio.

- Fuente primaria (versionada, siempre presente): consulta
  `docs/reference/public/claude-code-best-practices.md` y alinea las
  sugerencias con lo que documenta. Está basada en documentación pública de
  Anthropic (docs.claude.com, anthropic.com/engineering) — su contenido sí
  puede citarse y reproducirse en código, commits, PRs o documentación
  versionada.
- Fuente secundaria y opcional (local, confidencial, ver
  `docs/reference/README.md`): si además existe
  `docs/reference/Anthropic Certification Exam Guide June 2026.md` en el
  árbol de trabajo, úsala solo para matizar decisiones de diseño — nunca
  cites ni reproduzcas su contenido textual en código, commits, PRs o
  documentación versionada. Si no existe (ej. clon nuevo sin el material
  repuesto), continúa solo con la fuente primaria; no es una dependencia del
  build ni de la app.
- No apliques esta guía a tareas de dominio puro (facturación, entidades,
  reportes Jasper) salvo que el usuario la mencione explícitamente — para eso
  ya existen `jpa-entities.md`, `jasper-reports.md` y `tests.md`.
