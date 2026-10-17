# Migraciones de base de datos

> Fuente de verdad del esquema: los archivos `V*.sql` de esta carpeta.
> Especificación funcional: [`docs/requirements/requirements-v1.0.md`](../../../../../../docs/requirements/requirements-v1.0.md) §7.
> Convenciones aplicadas: [`docs/development/spring-conventions.md`](../../../../../../docs/development/spring-conventions.md) §4.4.

---

## 1. La regla que no se negocia

> **Una migración aplicada NO se edita jamás. Jamás. Ni para corregir un typo.**

Flyway calcula el **checksum** de cada migración al aplicarla y lo compara en cada arranque posterior.
Si editas un archivo ya aplicado, el checksum cambia y la aplicación **falla al arrancar** con
`Validate failed: Migration checksum mismatch`.

No es un castigo: es la única forma de garantizar que todas las bases de datos del mundo —la de tu
máquina, la de tus compañeros, la de integración, la de producción— tienen **exactamente** el mismo
esquema. Editar una migración aplicada produce bases distintas con el mismo número de versión, que es
la peor forma de fallo posible: no se rompe al instante, se rompe meses después y en otro sitio.

**Si algo está mal, se arregla con una migración nueva:**

```sql
-- ❌ Editar V1__baseline_schema.sql          → rompe el arranque
-- ✅ Crear V2__fix_...sql                     → se aplica en orden y todo el mundo avanza
```

---

## 2. Nomenclatura

| Elemento | Formato | Ejemplo |
|---|---|---|
| Nombre | `V<versión>__<descripcion_en_ingles>.sql` | `V2__add_pizza_image_key.sql` |
| Separador de versión y descripción | **Doble guion bajo** `__` | — |
| Descripción | En **inglés**, en `snake_case`, sin acentos | `baseline_schema` |
| Una migración | **Una sola responsabilidad** | No mezclar un índice y un cambio de columna |

La versión es un entero creciente. No se reutiliza ni se renumera aunque la migración se descarte: si
`V3` nunca llegó a aplicarse a ningún sitio, se puede reciclar; si llegó a aplicarse **en cualquier
lugar**, es intocable para siempre.

---

## 3. Qué se escribe y qué no

| En una migración `V*` | Nunca en una migración `V*` |
|---|---|
| Tablas, columnas, índices, restricciones | **Datos de prueba.** Van en el perfil `test` o en `scripts/` |
| Funciones y *triggers* | Datos reales de ningún tipo |
| Migraciones de datos **reproducibles e idempotentes** | Números de teléfono, correos o nombres de personas |

**Por qué los datos de prueba están prohibidos aquí.** Una migración con datos insertados se ejecuta en
producción y en el entorno de integración. Los datos de prueba se multiplican, ensucian el historial y
terminan en una tabla de pedidos de una pizzería real.

Si alguna vez hace falta una migración de datos legítima, se hace **idempotente** y aparte, con su
propio archivo `V` y su propio ADR explicando por qué.

---

## 4. Convenciones del esquema

Estas reglas se aplican a **todas** las migraciones, no sólo a la primera.

| Regla | Detalle | Por qué |
|---|---|---|
| **Identificadores** | `UUID` **sin** `DEFAULT gen_random_uuid()` | El agregado necesita su id **antes** de persistirse; si lo genera la base, hay que recuperarlo después de insertar |
| **Dinero** | `NUMERIC(12,0)` | El COP no tiene decimales (RN-IM-05). Con escala 0 el `CHECK line_total = unit_price * quantity` es una igualdad **exacta** y el motor la usa como integridad real |
| **Fechas** | `TIMESTAMPTZ`, siempre en UTC | Sin zona horaria, un `TIMESTAMP` no sabe qué es «medianoche» |
| **Enumerados** | `CHECK` con literales, **nunca** tipos `ENUM` | Alterar un `ENUM` exige `ALTER TYPE` y bloquea la tabla; un `CHECK` no tiene nada que alterar |
| **Claves foráneas** | Siempre con `ON DELETE RESTRICT` explícito | Nunca `CASCADE` sobre datos de negocio: un borrado accidental no puede arrastrar un pedido |
| **Unicidad insensible a mayúsculas** | `CREATE UNIQUE INDEX ... ON t (lower(col))` | PostgreSQL **rechaza** una expresión dentro de `UNIQUE (lower(email))` de tabla; sólo se puede en un índice |
| **Texto indexado** | `VARCHAR(254)` como máximo | Un índice btree sobre texto no puede pasar de ~2704 bytes, y 255 caracteres UTF-8 pueden pasarse |
| **Índices** | Sólo para consultas reales | Un índice por columna no es «más seguro»: encarece cada escritura y no acelera nada |
| **Nombres** | Inglés en tablas y columnas, español en los comentarios | Igual que el código |

---

## 5. Reglas que se verifican solas

| Restricción | Dónde | Qué impide |
|---|---|---|
| `UNIQUE (order_id)` en `cash_payments` | `V1` | **Doble cobro.** Dos peticiones simultáneas no pueden insertar dos filas (RN-PG-07) |
| `UNIQUE (pizza_id, size)` en `pizza_variants` | `V1` | Dos variantes del mismo tamaño en una pizza |
| `UNIQUE (lower(email))` en `customers` | `V1` | Darse de alta dos veces cambiando las mayúsculas |
| `CHECK (price > 0)` | `V1` | Precio cero o negativo (RN-IM-07) |
| `CHECK (quantity BETWEEN 1 AND 20)` | `V1` | Cantidades imposibles (RN-DS-04) |
| `CHECK (line_total = unit_price * quantity)` | `V1` | Una línea cuyo total no cuadra con su propio precio |
| *Constraint trigger* `verificar_total_pedido` | `V1` | Un pedido cuyo total **no** sea la suma de sus líneas (RN-IM-03) |

La última merece una explicación: **no** puede ser un `CHECK`, porque PostgreSQL no admite subconsultas
en un `CHECK`. Y **no** puede ser un trigger normal, porque al crear un pedido se inserta primero la
cabecera y después las líneas: un trigger de fila se dispararía sobre un pedido aún a medio construir.
Por eso es un `CONSTRAINT TRIGGER ... DEFERRABLE INITIALLY DEFERRED`, que se ancla al **commit**, cuando
el total y las líneas ya son los definitivos.

---

## 4 bis. Un límite que la base de datos NO cubre

**RN-IM-06 dice que un importe fraccionario es un error de programación y debe rechazarse, nunca
redondearse. La base de datos no puede cumplirlo.**

Al convertir a `NUMERIC(12,0)`, PostgreSQL **redondea en silencio**:

| Valor enviado | Valor almacenado |
|---|---|
| `12500.50` | `12501` |
| `0.4` | `0` |
| `0.5` | `1` |

Comprobado el 2026-10-11 con una prueba dedicada (`SchemaMigrationIT`), que documenta el comportamiento
en lugar de taparlo.

**Consecuencia:** la garantía del redondeo vive **únicamente** en el objeto de valor `Money` del dominio,
que usa `setScale(0, RoundingMode.UNNECESSARY)` y **lanza excepción antes de que el importe llegue a la
base** ([`conventions.md` §4](../../../../../../docs/development/conventions.md)). Ninguna restricción SQL
podría evitarlo: el valor ya ha sido convertido cuando la base lo ve.

Por qué importa: si alguien añadiera una ruta que construyera un importe sin pasar por `Money`, el
redondeo pasaría desapercibido. La regla que lo evita no es una restricción, es **no saltarse `Money`**.

---

## 6. Cómo se aplica

Flyway se ejecuta **automáticamente al arrancar la aplicación**. No hay paso manual: si la base está
vacía, se crea el esquema; si ya está al día, no hace nada.

```powershell
# Comprobar que el historial está limpio
docker compose -f infra/local/docker-compose.yml exec db psql -U pizzeria_dev -d pizzeria -c \
  "select version, description, success from flyway_schema_history order by installed_rank;"
```

En las pruebas de integración, cada test aplica las migraciones sobre un PostgreSQL real de Testcontainers.
**Nunca H2**: H2 miente sobre tipos numéricos y restricciones, que es justo lo que hay que verificar.

---

## 7. Procedimiento para cambiar el esquema

1. Escribir el `V<n+1>__<descripcion>.sql` **en la misma historia** que lo necesita.
2. Si toca una regla de negocio, actualizar **a la vez** `requirements-v1.0.md`, este README y
   `spring-conventions.md` §4.4. Una historia no se cierra con documentación desfasada.
3. Verificar que se aplica sobre una base **vacía** y que la aplicación arranca con `ddl-auto=validate`.
4. No editar ninguna migración anterior. Jamás.

---

## 8. Estado

| Versión | Descripción | Fecha | Contenido |
|---|---|---|---|
| `V1` | `baseline_schema` | 2026-10-11 | Las 8 tablas del modelo de datos, sus índices, restricciones, y el *trigger* de coherencia del total |