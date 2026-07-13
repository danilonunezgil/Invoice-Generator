# Claude Code — buenas prácticas (fuente pública)

Notas propias basadas en documentación pública de Anthropic. Este archivo
**sí se versiona**: no reproduce texto de ningún material con copyright, solo
resume en palabras propias lo publicado en docs.claude.com y
anthropic.com/engineering, con link a la fuente de cada sección para que
cualquiera pueda verificarlo.

Es la fuente primaria de `.claude/rules/claude-code-architect-guide.md`. Si
querés profundizar en un tema, andá directo al link — este archivo es un
resumen de trabajo, no un reemplazo de la doc oficial.

## CLAUDE.md y memoria de proyecto
- `CLAUDE.md` es el lugar para contexto que Claude no puede derivar leyendo
  el código: convenciones del equipo, comandos de build/test, decisiones de
  arquitectura no obvias.
- Evitar duplicar ahí lo que ya es evidente desde el propio código (nombres
  de paquetes, stack, estructura de carpetas) — eso genera desincronización.
- Fuente: https://docs.claude.com/en/docs/claude-code/memory

## Reglas con scoping por path (`.claude/rules/`)
- Permiten cargar contexto/instrucciones solo cuando se toca cierta parte del
  árbol (frontmatter `paths:`), en vez de inflar el `CLAUDE.md` global.
- Útil para convenciones específicas de una capa (ej. `jpa-entities.md`,
  `jasper-reports.md` en este repo) que no aplican al resto del proyecto.
- Fuente: https://docs.claude.com/en/docs/claude-code/overview

## Subagentes
- Convienen para tareas de investigación/verificación que no necesitan
  ensuciar el contexto principal con output intermedio (búsquedas largas,
  exploración de código).
- Un subagente sin contexto previo necesita un prompt autocontenido: explicar
  qué se busca y por qué, no asumir que "sabe" lo que el agente principal ya
  investigó.
- Fuente: https://docs.claude.com/en/docs/claude-code/sub-agents

## Hooks
- Permiten interceptar eventos del propio harness (antes/después de una
  tool call) para reforzar reglas que de otra forma dependerían de que el
  modelo "se acuerde" — ej. bloquear una edición o un commit que viola una
  invariante del proyecto.
- Son deterministas: no dependen de que el modelo decida seguir la regla, la
  hacen cumplir a nivel de proceso.
- Fuente: https://docs.claude.com/en/docs/claude-code/hooks-guide

## Permisos y settings
- `settings.json` (versionado) vs `settings.local.json` (no versionado, por
  máquina/usuario) — separar lo que es convención de equipo de lo que es
  preferencia personal.
- Preferir allow/deny explícitos y acotados sobre modos permisivos amplios,
  sobre todo para comandos destructivos o que tocan estado compartido.
- Fuente: https://docs.claude.com/en/docs/claude-code/settings

## Flujo agéntico general (plan → ejecutar → verificar)
- Para tareas no triviales, separar la fase de planificación (alinear el
  approach) de la fase de ejecución reduce retrabajo.
- Verificar el resultado ejecutando/probando el cambio real, no solo
  confiando en que el diff "se ve bien".
- Fuente: https://www.anthropic.com/engineering/claude-code-best-practices

---
_Este archivo es un resumen de trabajo propio, no una guía oficial. Para
certificación o estudio formal, la fuente autoritativa es siempre la
documentación de Anthropic enlazada arriba._
