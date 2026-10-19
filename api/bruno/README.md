# Colección de Postman

[`pizzeria.postman_collection.json`](pizzeria.postman_collection.json) sirve para probar la API a
mano. En Postman: **Import → Import** → el fichero.

## Antes de usarla

```powershell
pwsh scripts/dev-up.ps1 -Detach   # levanta PostgreSQL y la aplicación
pwsh scripts/db.ps1  -Accion seed # carga las 6 pizzas de ejemplo
```

El catálogo usa **identificadores numéricos** desde la migración `V2`. Con el seed recién cargado en
una base vacía quedan:

| id | Pizza | Para probar |
|---|---|---|
| 1 | Margarita | El detalle con los tres tamaños |
| 2 | Bufala | 404: su única variante está agotada |
| 3 | Diavola | Listado |
| 4 | Capricciosa | Listado con el grande agotado |
| 5 | Vegetariana | 404: no tiene ninguna variante |
| 6 | Retirada | 404: está dada de baja |

```powershell
curl http://localhost:8080/api/v1/pizzas/1
```

> **Ojo si tu base ya existía antes de la migración `V2`.** Los ids los asignó la migración
> (`row_number()` por antigüedad y nombre), no el seed, así que pueden no coincidir con la tabla
> anterior: en el laboratorio quedó `1` Bufala, `2` Capricciosa, `3` Diavola, **`4` Margarita**,
> `5` Retirada y `6` Vegetariana. Para tener los ids del seed, empezar de cero:
>
> ```powershell
> pwsh scripts/db.ps1 -Accion reset -Force
> pwsh scripts/db.ps1 -Accion seed
> ```
>
> Y para saber los tuyos, sin suponer nada:
>
> ```powershell
> pwsh scripts/db.ps1 -Accion shell
> select id, name from pizzas order by id;
> ```

## Variables

| Variable | Para qué |
|---|---|
| `baseUrl` | `http://localhost:8080` |
| `token` | Lo rellena la petición de login |
| `pizzaId` | Lo rellena «Crear pizza» |
| `variantId` | Lo rellena «Añadir variante» |

Con las peticiones de la carpeta `Pizzas — Admin` el flujo es: login → crear pizza (guarda su id) →
añadir variante (guarda su id) → actualizar. Para probar contra el seed, pon `1` en `pizzaId`.

## Rutas que ya existen

Sólo hay dos endpoints implementados (US-010): `GET /api/v1/pizzas` y `GET /api/v1/pizzas/{pizzaId}`.
Las peticiones de `Auth` y de `Pizzas — Admin` responden `404` hasta que lleguen sus historias: son
las que se van a ir rellenando según se implementen.

## Dónde está la verdad

El contrato es [`docs/architecture/api-contract.md`](../../docs/architecture/api-contract.md) y el
OpenAPI vivo, en `/swagger-ui/index.html` con el perfil `local`. Si la colección y ese documento
divergen, **manda el documento**.

## Al añadir un endpoint

1. En la carpeta de su recurso (`Auth`, `Pizzas — Público`, `Pizzas — Admin`, …).
2. Con `{{baseUrl}}` y las variables de arriba, nunca un id escrito a mano.
3. Con la respuesta real guardada como ejemplo, para poder mirarla sin ejecutarla.