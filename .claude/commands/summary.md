---
description: Muestra un resumen rápido de datos en Postgres — cuántos clientes hay, cuántas facturas se han emitido y en qué estado están cada una. Úsalo para chequear el estado actual de los datos sin abrir un cliente SQL.
---
Corre estas queries contra la base de datos local vía `docker compose exec db psql` (usa las
credenciales de `docker-compose.yml`: usuario `invoice`, base `invoice_generator`; si el
contenedor no está arriba, dile al usuario que corra `docker compose up -d db` primero) y
presenta un resumen legible en texto, no la salida cruda de psql:

1. Total de clientes:
   ```sql
   SELECT count(*) AS total_customers FROM customers;
   ```

2. Total de facturas y desglose por estado:
   ```sql
   SELECT status, count(*) AS total
   FROM invoices
   GROUP BY status
   ORDER BY status;
   ```

Combina ambos resultados en un resumen tipo:

```
Clientes: N
Facturas: M total
  - DRAFT: x
  - ISSUED: x
  - PAID: x
  - CANCELLED: x
```

Si algún estado de `InvoiceStatus` (domain/InvoiceStatus.java) no aparece en el resultado, es
porque no hay facturas en ese estado — muéstralo igual con 0, no lo omitas.
