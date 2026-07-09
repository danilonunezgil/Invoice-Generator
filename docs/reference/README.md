# docs/reference/ — material confidencial local

Esta carpeta guarda material de referencia que **no debe publicarse** en el repositorio
(ej. la guía oficial de certificación de Anthropic). Todo su contenido está en
`.gitignore` excepto este README.

## Cómo reponerlo en una máquina nueva

1. Convierte el PDF de la guía oficial a Markdown.
2. Guárdalo en esta carpeta, ej: `docs/reference/anthropic-architect-foundations-guide.md`.
3. No lo renombres a algo que empiece distinto si vas a referenciarlo desde reglas o
   CLAUDE.md — mantén el nombre consistente entre máquinas.

## Por qué existe esta carpeta versionada si su contenido está ignorado

Para que la estructura y la convención sobrevivan a un `git clone` en otra máquina,
aunque el archivo confidencial en sí no viaje con el repo.
