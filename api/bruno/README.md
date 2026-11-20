# Colección de Postman

[`pizzeria.postman_collection.json`](pizzeria.postman_collection.json) sirve para probar la API a
mano. En Postman: **Import → Import** → el fichero.

## Antes de usarla

```powershell
pwsh scripts/dev-up.ps1 -Detach   # levanta PostgreSQL y la aplicación
pwsh scripts/db.ps1  -Accion seed # carga categorías y productos de ejemplo
```

El catálogo usa **identificadores numéricos**. Con el seed recién cargado en una base vacía hay
categorías (`Classic Pizzas`, `Special Pizzas`, `Drinks`) y productos como `Margarita`, `Pepperoni` o
`Hawaiian`:

```powershell
curl "http://localhost:8080/api/v1/products?onlyAvailable=true"
curl http://localhost:8080/api/v1/products/1
```

Los ids los asigna la base (`IDENTITY`), así que si tu volumen ya tenía datos pueden no empezar en 1.
Para saber los tuyos sin suponer nada:

```powershell
pwsh scripts/db.ps1 -Accion shell
select id, name from categories order by id;
select id, name, category_id from products order by id;
```

## Variables

| Variable | Para qué |
|---|---|
| `baseUrl` | `http://localhost:8080` |
| `token` | Lo rellena la petición de login |
| `productId` | Id de un producto existente, para el detalle |
| `categoryId` | Id de una categoría, para filtrar el listado |
| `orderId` | Lo rellena «Crear pedido» |
| `addressId` | Lo rellena «Crear direccion» |

Para probar el detalle, pon un id real del seed en `productId`.

## Rutas que ya existen

La fuente de verdad es la tabla «Operaciones» de
[`docs/architecture/api-contract.md`](../../docs/architecture/api-contract.md): ahí está cada ruta y
si ya está hecha. Las peticiones de esta colección corresponden a esa lista, y las que faltan se van
rellenando a medida que se cierra cada historia.

## La carpeta Direcciones

`POST /api/v1/orders` exige un `addressId` **que sea del cliente que hace la petición**. La carpeta
`Direcciones` es la que lo pone a mano disponible:

1. `Login` (rellena `{{token}}`).
2. `Crear dirección` → guarda el `id` de la respuesta.
3. `Listar mis direcciones` → ahí está el `id` que hay que pegar en `Crear pedido`.

Si el `addressId` no existe o es de otro cliente, `POST /api/v1/orders` devuelve **404
`IDENTITY_ADDRESS_NOT_FOUND`** — nunca un 500. Antes de que existiera esta validación devolvía
`500 INTERNAL_ERROR` por una violación de clave ajena (`fk_orders_address`).

## Dónde está la verdad

El contrato es [`docs/architecture/api-contract.md`](../../docs/architecture/api-contract.md) y el
OpenAPI vivo, en `/swagger-ui/index.html` con el perfil `local`. Si la colección y ese documento
divergen, **manda el documento**.

## Al añadir un endpoint

1. En la carpeta de su recurso (`Auth`, `Categorias — Publico`, `Productos — Publico`, …).
2. Con `{{baseUrl}}` y las variables de arriba, nunca un id escrito a mano.
3. Con la respuesta real guardada como ejemplo, para poder mirarla sin ejecutarla.