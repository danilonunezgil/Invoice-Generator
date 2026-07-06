---
paths:
  - **/domain/**
---
# Convenciones de entidades
- Usar @Version para optimistic locking en Invoice
- IDs siempre UUID, nunca Long autoincremental
- BigDecimal para todo monto, nunca double/float