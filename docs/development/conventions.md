# Convenciones de código

> Documento normativo. Complementa a [`AGENTS.md`](../../AGENTS.md) y a [`spring-conventions.md`](spring-conventions.md).
> Ante cualquier conflicto: `AGENTS.md` manda.

---

## 1. Idiomas

| Elemento | Idioma |
|---|---|
| Paquetes, clases, métodos, variables, constantes | **Inglés** |
| Nombres de negocio (`Pizza`, `Order`, `Pending`) | **Inglés** |
| Comentarios, Javadoc y documentación | **Español** |
| Mensajes de error expuestos al cliente | **Español**, sin datos sensibles |
| `code` de los errores en la API | **Inglés**, estable y en mayúsculas (`ORDERS_NOT_FOUND`) |

---

## 2. Estructura y tamaño

| Regla | Valor |
|---|---|
| Paquete base | `com.pizzeria` |
| Capas globales, no módulos | `com.pizzeria.domain`, `…application`, `…infrastructure`, `…shared` ([ADR-0004](../adr/0004-estructura-de-paquetes-por-capas.md)) |
| Paquetes de primer nivel | **Sólo cuatro**: `domain`, `application`, `infrastructure`, `shared`. Un quinto es un fallo de build |
| Imports | Explícitos. **Prohibido** el comodín `*` |
| Tamaño de clase | Máximo ~300 líneas. Si se supera, se divide por responsabilidad |
| Tamaño de método | Máximo ~15 líneas. Si supera, se extrae o se refactoriza |
| Profundidad de anidación | Máximo 3 niveles |
| Imports de lombok | **Prohibidos en todo el proyecto** |

**Por qué sin Lombok.** Lombok genera código que no se ve al leer la clase y añade un processor de anotaciones al build. Para un proyecto de portafolio, el argumento de "código explícito" outweighs el ahorro de líneas. Si más adelante hay una decisión contraria, se escribe un ADR.

---

## 3. Modelado de dominio

### 3.1 Entidades

```java
/**
 * Entidad de dominio que representa una pizza del catálogo.
 */
public final class Pizza {

    private final UUID id;
    private final String name;
    private final String description;
    private final boolean active;
    // ...
}
```

- **Inmutabilidad por defecto.** Los campos son `final` salvo que haya una razón de negocio para mutarlos (los agregados con máquina de estados son el caso legítimo).
- Sin setters públicos. Los cambios de estado se expresan como **métodos de negocio** (`order.confirm(now)`), nunca como `setStatus(...)`.
- El constructor es **privado**. Se crean a través de factorías estáticas o de un método de reconstitution (`Order.reconstitute(...)`) usado por el adaptador de persistencia.
- Sin anotaciones: ni `@Entity`, ni `@Getter`, ni Lombok.

### 3.2 Objetos de valor

Cuando un dato tiene reglas propias (dinero, cantidad, tamaño), se encapsula:

La moneda del proyecto es el **peso colombiano (COP)**, que no tiene subdivisión decimal. Eso cambia dos
decisiones que en una moneda de dos decimales serían distintas:

```java
/**
 * Objeto de valor para importes en pesos colombianos.
 *
 * El COP no tiene centavos en uso comercial, así que el importe es siempre un entero.
 * Redondear no es una decisión de negocio: si aparece una fracción, es un error de
 * programación y se lanza una excepción en lugar de ocultarlo.
 */
public record Money(BigDecimal amount, String currency) {

    private static final int SCALE = 0;
    private static final RoundingMode ROUNDING = RoundingMode.UNNECESSARY;

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        amount = amount.setScale(SCALE, ROUNDING);   // lanza si el importe tiene fracción
        if (amount.signum() < 0) {
            throw new InvalidMoneyException(amount, currency);
        }
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount()), currency);
    }

    public Money multiply(int times) {
        return new Money(amount.multiply(BigDecimal.valueOf(times)), currency);
    }
}
```

Reglas:

- Un value object es **inmutable** y valida sus invariantes en el constructor compacto.
- Se compara por **valor**: los `record` de Java lo dan de forma gratuita.
- **Dinero = `Money`**, nunca `BigDecimal` suelto en la firma de un método de dominio.

### 3.3 Cantidades

- `quantity` se modela como `int` validada (1–20 por línea) o como un value object `Quantity` si aparecen más reglas. **No** se usa el tipo primitivo sin validación en la frontera.

---

## 4. Dinero: reglas obligatorias

```java
// Correcto
BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(0, RoundingMode.UNNECESSARY);

// Prohibido
double total = unitPrice * quantity;          // Error de precisión binaria
new BigDecimal(12.505).setScale(0, RoundingMode.HALF_UP);  // Oculta un error: ¿debería ser 13?
```

| Regla | Detalle |
|---|---|
| Tipo | `BigDecimal` exclusivamente. `double`/`float` prohibidos en todo el proyecto |
| Moneda | **COP** (peso colombiano), decidido en `requirements-v1.0.md` §RN-IM-05 |
| Escala | **0 decimales**, fijada explícitamente con `setScale(0, …)` |
| Redondeo | `RoundingMode.UNNECESSARY`, en una **constante con nombre**. Provoca excepción ante una fracción |
| Comparación | Sólo con `compareTo`, nunca con `equals` |
| Persistencia | `NUMERIC(12,0)` en PostgreSQL |
| Presentación | Miles con punto y sin decimales: `12.500 COP` |

**Por qué `UNNECESSARY` y no `HALF_UP`.** En una moneda sin decimales no hay ninguna regla de negocio que
decida hacia dónde redondear. El único resultado válido es el entero exacto. Si una operación produce
fracciones, hay un error de programación (una división inesperada, una conversión mal hecha) y lo
correcto es que **falle de forma visible**, no que se ajuste en silencio.

**Pruebas obligatorias** de todo caso de uso que calcula importes:

| Caso | Qué demuestra |
|---|---|
| Suma de varios valores enteros | El total es la suma exacta |
| `12.505` como precio | Se **rechaza** la fracción, no se redondea a 13 |
| `0 × 40.000` | Cero correctamente, sin caso especial |
| Importe muy alto | No hay desbordamiento en el rango del negocio |

| Segundo caso | El importante: verifica que la regla de negocio no está corrupta por un redondeo implícito |

---

## 5. Colecciones y nulidad

| Situación | Regla |
|---|---|
| Puerto de salida que puede no encontrar nada | Devuelve `Optional<T>`, **nunca** `null` |
| Puerto de salida que devuelve colección | Lista vacía, **nunca** `null` |
| Campo de entidad | **Nunca** `Optional`. Se usa `@Nullable` o el objeto vacío del dominio |
| Cadenas | **Nunca** `null` ni vacío; se valida en el constructor |
| Retorno de método | No se devuelve `null` salvo que esté documentado con `@Nullable` |

---

## 6. Excepciones

Jerarquía:

```
RuntimeException
└── DomainException (abstracta, en domain)        → problema del negocio, esperado
    ├── CatalogException
    ├── OrdersException
    │   ├── OrderNotFoundException
    │   ├── InvalidOrderTransitionException
    │   └── EmptyOrderException
    └── IdentityException

RuntimeException
└── ApplicationException (abstracta, en application) → fallo de un caso de uso
    └── PaymentAlreadyRegisteredException
```

| Regla | Detalle |
|---|---|
| Se lanzan | Excepciones de dominio con nombre. Nunca `IllegalArgumentException` como mecanismo de negocio |
| Se capturan | Sólo donde se pueda hacer algo distinto de propagar |
| **Prohibido** | `catch (Exception e) {}` vacío. Si se captura: se registra **o** se propaga |
| **Prohibido** | Usar excepciones para control de flujo normal (devolver un valor de estado) |
| Mensaje | Describe el problema de negocio, no la mecánica. Sin identificadores internos ni SQL |
| Excepciones técnicas | Se dejan propagar; el `@RestControllerAdvice` las traduce a 500 genérico |

Cada excepción de dominio que se traduzca a un endpoint tiene su test en la matriz de errores.

---

## 7. Nombres con nombre

Cero números mágicos y cero literales sueltos con significado de negocio:

```java
// Prohibido
if (items.size() > 10) { ... }

// Correcto
public static final int MAX_DISTINCT_ITEMS_PER_ORDER = 10;
if (items.size() > MAX_DISTINCT_ITEMS_PER_ORDER) { ... }
```

Lo mismo con estados, roles, métodos de pago y tamaños. Los enums ya son constantes con nombre: `OrderStatus.PENDING`.

---

## 8. Registro de logs

```java
// Correcto
log.info("Order {} confirmed for customer {}", orderId, customerId);

// Prohibido
log.info("Order " + orderId + " confirmed");
System.out.println("pedido creado");
```

| Regla | Detalle |
|---|---|
| API | SLF4J con `{}` y argumentos |
| Niveles | `ERROR` fallo esperado y accionable · `WARN` anómalo pero recuperado · `INFO` hito de negocio · `DEBUG` diagnóstico interno · `TRACE` apagado en producción |
| **Prohibido** | Contraseñas, tokens JWT, cabeceras `Authorization`, correos completos, SQL con parámetros de usuario, pilas de excepciones en `INFO`/`WARN` |
| Formato | JSON estructurado en `local` y `prod`; legible sólo si se configura así |
| Correlación | Un identificador de petición propagado y devuelto en la respuesta |

---

## 9. Concurrencia y tiempo

- **Prohibido** `LocalDateTime.now()`, `Instant.now()` o `new Date()` dentro del dominio y de los casos de uso.
- El tiempo se inyecta como `java.time.Clock` (puerto propio en `domain.port.in`, bean en `infrastructure.config`). Así los tests son deterministas sin ninguna librería extra.
- El identificador UUID lo genera la aplicación, no la base de datos: el agregado necesita su id antes de persistirse.

---

## 10. Qué se escribe y cómo se revisa

| Práctica | Cómo |
|---|---|
| Public methods | Javadoc cuando la intención no sea obvia; **no** reiterar el nombre |
| Clases | Una frase de propósito en la cabecera |
| Algoritmo no trivial | Comentario con el *porqué*, no el *qué* |
| Código obvio | Sin comentario |
| `TODO` | **Prohibido** en código de producción. Trabajo pendiente → `docs/planning/backlog.md` |

---

## 11. Anti-patrones rechazados explícitamente

| Patrón | Por qué se rechaza |
|---|---|
| Utilidades estáticas globales (`StringUtils`, `DateUtils`) | Acoplamiento oculto; cada función tiene un dueño natural |
| `Manager` o `Helper` genérico | No dice qué hace. Se prefiere un nombre del dominio |
| `try { ... } catch (Exception) { return null; }` | Destruye la información de error |
| DTO anidado en `domain` | El dominio no conoce el transporte |
| Comentario que repite el código | Ruido; si el código necesita comentario para entenderse, hay que renombrar |

---

## 12. Checklist de revisión de código

Antes de dar una historia por terminada:

- [ ] ¿Hay alguna dependencia prohibida por capa? (lo comprueba ArchUnit, pero revisa mentalmente)
- [ ] ¿Los importes se calculan con `BigDecimal` y redondeo explícito?
- [ ] ¿El `Clock` está inyectado en lugar de llamar a `now()`?
- [ ] ¿Hay algún `null` que debería ser `Optional` o lista vacía?
- [ ] ¿Los logs usan `{}` y no contienen datos sensibles?
- [ ] ¿Hay algún número mágico?
- [ ] ¿Las excepciones están tipadas y el mensaje no filtra información?
- [ ] ¿Los tests cubren el caso negativo, no sólo el feliz?
- [ ] ¿El identificador es inglés y el comentario español?
- [ ] ¿Hay `TODO`, `@Disabled` o `System.out`?