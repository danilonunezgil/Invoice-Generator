---
paths: ["**/reports/**", "**/*.jrxml"]
---
# Convenciones JasperReports
- Las plantillas .jrxml se compilan en build time, no en runtime (usa el maven-jasperreports-plugin)
- Todo campo del datasource debe tener getter Java bean estándar (JRBeanCollectionDataSource lo requiere)
- Nombrar subreports con prefijo `sub_`