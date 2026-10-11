# Definition of Done — Política global

> Una historia **no** está terminada porque compile. Está terminada cuando cumple **todos** los criterios aplicables
> y existe evidencia verificable de cada uno.

---

## 1. Criterios

| # | Criterio | Cómo se verifica | Aplica a |
|---|---|---|---|
| 1 | **Criterios de aceptación cumplidos** | Lista marcada; cada punto con su prueba asociada | Todas |
| 2 | **Arquitectura y reglas de dependencia respetadas** | Test ArchUnit verde | Todas |
| 3 | **Pruebas del comportamiento nuevo y de sus fallos** | Tests nuevos ejecutados, con resultado reportado | Todas |
| 4 | **Validaciones ejecutadas y en verde** | `./mvnw verify` ejecutado por quien cierra la historia, con la salida real | Todas |
| 5 | **Sin simulaciones** | Sin `TODO`, `@Disabled`, `System.out`, `return null`, mocks fijados ni *feature flags* que finjan comportamiento | Todas |
| 6 | **Migraciones versionadas e inmutables** | Aplicadas desde cero en una base vacía | Historias de datos |
| 7 | **Sin secretos ni datos sensibles** | `.env` no versionado · escaneo de secretos limpio · logs sin contraseñas ni tokens | Todas |
| 8 | **Documentación actualizada** | Si cambió contrato, esquema o decisión, el documento correspondiente está en el mismo cambio | Todas |
| 9 | **OpenAPI regenerado** | Si cambió la API, el snapshot está actualizado | Historias de API |
| 10 | **Seguridad y permisos revisados** | Matriz de autorización si la historia expone o modifica endpoints | Historias de API |
| 11 | **Cambios revisables y reproducibles** | Un commit atómico con mensaje en Conventional Commits | Todas |
| 12 | **Diagramas coherentes** | Si cambió el modelo o la arquitectura, el `.drawio` correspondiente está actualizado | Cuando aplique |

---

## 2. Qué **no** cumple la Definition of Done

| Excusa | Por qué no vale |
|---|---|
| «Compila, ya estará» | Compilar no demuestra comportamiento ni que los tests existen |
| «Los tests pasan en local» | Hay que ejecutarlos y **reportar la salida**; no basta con afirmar que pasan |
| «Es un detalle, lo arreglo luego» | Un `TODO` en el código es deuda invisible; lo pendiente va en el backlog |
| «El test lo escribo después» | Sin test, la historia no existe a efectos de verificación |
| «Sólo es un cambio pequeño» | El tamaño no exime de criterios: reduce el riesgo, no las obligaciones |
| «El contrato no ha cambiado» | Entonces OpenAPI y `api-contract.md` tampoco deberían haber cambiado; hay que comprobarlo |
| «No hay nada sensible» | Ante la duda, es sensible. Se revisa con el checklist de abajo |

---

## 3. Checklist de revisión antes de cerrar

### Código

- [ ] ¿Hay alguna dependencia prohibida por capa?
- [ ] ¿Los importes se calculan con `BigDecimal`, escala 0 (COP) y `RoundingMode.UNNECESSARY`?
- [ ] ¿El tiempo viene de un `Clock` inyectado y no de `now()`?
- [ ] ¿Hay algún `null` que debería ser `Optional` o lista vacía?
- [ ] ¿Los logs usan `{}` y no contienen datos sensibles?
- [ ] ¿Hay números mágicos sin nombre?
- [ ] ¿Las excepciones están tipadas y el mensaje no filtra información?
- [ ] ¿Hay algún `System.out`, `printStackTrace` o `TODO`?
- [ ] ¿Los tests cubren el caso negativo, no sólo el feliz?

### Datos

- [ ] ¿La migración se aplica sobre una base vacía?
- [ ] ¿`ddl-auto=validate` y no `create`/`update`?
- [ ] ¿Alguna migración `V` modificada después de su merge?
- [ ] ¿Las restricciones de integridad están en la base de datos?

### API y seguridad

- [ ] ¿Los códigos de respuesta HTTP son los correctos?
- [ ] ¿Los errores salen como `application/problem+json`?
- [ ] ¿La propiedad del recurso se filtra **en la consulta**?
- [ ] ¿Los endpoints administrativos exigen rol ADMIN?
- [ ] ¿El cliente puede inyectar precios o totales? Si puede, es un fallo.
- [ ] ¿Hay alguna forma de registrarse como ADMIN?

### Verificación

- [ ] `./mvnw verify` ejecutado y en verde, con la salida reportada
- [ ] Tests ArchUnit verdes
- [ ] Sin secretos (`gitleaks detect`)
- [ ] Documentación actualizada
- [ ] `backlog.md` actualizado con el estado

---

## 4. Excepciones

Una historia puede cerrarse con un criterio incumplido **sólo** si:

1. El criterio se registra explícitamente como pendiente en `backlog.md` con su identificador.
2. La razón está escrita (por ejemplo, «depende de US-060, que es del S7»).
3. El responsable lo autoriza.

**Nunca** se marca una historia como `Hecho` con la excepción implícita. Si hay una excepción, el estado es `Hecho con deuda` y la deuda está en el backlog.

---

## 5. Criterios de calidad diferenciados por capa

No todos los estratos merecen el mismo nivel de prueba. El coste de probar de más es real; probar de menos, también.

| Capa | Cobertura mínima | Qué se exige además |
|---|---|---|
| Dominio | ≥ 80 % | Todos los invariantes y todas las transiciones, incluidas las ilegales |
| Casos de uso | ≥ 70 % | Al menos un test por camino de excepción previsto |
| Adaptador web | Contractual | Cada endpoint: caso feliz, validación inválida, 401, 403 y 404 |
| Persistencia | Por consulta | Cada query crítica con su caso límite; constraints probadas en integración |
| Integración | Escenarios críticos | Los escenarios negativos definidos en el plan de pruebas |

**El porcentaje es un indicador, no un objetivo.** Si una clase está al 95 % porque sólo tiene getters, no aporta nada. Se revisa **qué** está probado, no **cuánto**.

---

## 6. Antipatrones de Definition of Done

| Antipatrón | Por qué falla |
|---|---|
| Marcar `Hecho` y arreglar después | La deuda se acumula y nadie la recuerda |
| Tests que sólo comprueban que no hay excepción | No verifican comportamiento; son decorativos |
| Un test por método sin casos negativos | No detecta la mayoría de los errores reales |
| Mocks que replican la implementación | Un test así pasa aunque el código esté mal |
| Subir cobertura bajando el número de aserciones | Métrica manipulada |
| Cerrar la historia sin leer el diff | Los secretos y los `System.out` se cuelan así |