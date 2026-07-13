# docs/reference/ — material de referencia

Esta carpeta tiene dos zonas con reglas distintas:

- `public/` — **versionada**. Notas propias basadas en documentación pública de
  Anthropic (docs.claude.com, anthropic.com/engineering). No contiene material
  con copyright de terceros, así que puede publicarse sin problema. Es la
  fuente primaria que usa `.claude/rules/claude-code-architect-guide.md`.
- Todo lo demás en esta carpeta (ej. la guía oficial de certificación) —
  **confidencial, nunca se publica**. Ignorado vía `.gitignore` salvo este
  README y `public/`.

## Cómo reponer el material confidencial en una máquina nueva

1. Convierte el PDF de la guía oficial a Markdown.
2. Guárdalo en esta carpeta con el nombre exacto que usa la regla:
   `docs/reference/Anthropic Certification Exam Guide June 2026.md`.
3. No lo renombres — `.claude/rules/claude-code-architect-guide.md` lo
   referencia por ese nombre exacto.

Este material es **opcional**: la regla usa `public/claude-code-best-practices.md`
como fuente primaria y funciona igual de bien sin el material confidencial
repuesto (ver el propio archivo de regla para el comportamiento exacto).

## Por qué existe esta carpeta versionada si parte de su contenido está ignorado

Para que la estructura y la convención sobrevivan a un `git clone` en otra
máquina, aunque el archivo confidencial en sí no viaje con el repo.
