---
name: onboard-customer-purchase
description: Da de alta un cliente nuevo y le emite una factura por un producto o servicio que acaba de adquirir, encadenando las llamadas REST correspondientes (crear cliente, crear borrador de factura, agregar línea, emitir). Úsalo cuando pidan registrar un cliente nuevo junto con su primera compra.
argument-hint: "[name] [taxId] [email] [regionCode] [product] [quantity] [unitPrice]"
arguments: [name, taxId, email, regionCode, product, quantity, unitPrice]
context: fork
allowed-tools: [Bash]
disallowed-tools: [Read, Write, Edit]
---

Uso: los 7 argumentos se sustituyen por posición separada por espacios, sin
parseo de comillas estilo shell — un valor con espacio interno (ej. un
nombre de cliente de dos palabras) rompe la alineación de todos los
argumentos siguientes. Usa guiones en vez de espacios (`Beta-Studios`,
`Consultoria-de-migracion`) hasta tener valores multi-palabra resueltos.

Da de alta al cliente y factúrale su compra, encadenando 4 llamadas a la API
local (`http://localhost:8080`). Si cualquier llamada falla por conexión,
dile al usuario que levante `docker compose up -d db` y
`./mvnw spring-boot:run`, y detente sin inventar datos. Si una llamada
devuelve 4xx/5xx, no sigas con el siguiente paso — reporta el error tal
cual y detente.

1. Crear el cliente:
   ```
   curl -sf -X POST http://localhost:8080/api/customers \
     -H "Content-Type: application/json" \
     -d '{"name":"$name","taxId":"$taxId","email":"$email","regionCode":"$regionCode"}'
   ```
   Guarda el `id` de la respuesta — lo necesitas en el paso 2.

2. Crear el borrador de factura para ese cliente. `dueDate` = hoy + 30 días,
   calculado con `date -d "+30 days" "+%Y-%m-%d"`:
   ```
   curl -sf -X POST http://localhost:8080/api/invoices \
     -H "Content-Type: application/json" \
     -d '{"customerId":"<id del paso 1>","dueDate":"<fecha calculada>"}'
   ```
   Guarda el `id` de la factura — lo necesitas en los pasos 3 y 4.

3. Agregar la línea del producto/servicio adquirido:
   ```
   curl -sf -X POST http://localhost:8080/api/invoices/<id de la factura>/line-items \
     -H "Content-Type: application/json" \
     -d '{"description":"$product","quantity":$quantity,"unitPrice":$unitPrice}'
   ```

4. Emitir la factura:
   ```
   curl -sf -X POST http://localhost:8080/api/invoices/<id de la factura>/issue
   ```

Al terminar, devuelve a la conversación principal SOLO 2 líneas: nombre del
cliente + su ID, y el número de factura emitido + su total — nada de los
JSON intermedios de los 4 pasos.
