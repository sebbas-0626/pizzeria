package com.pizzeria.domain.exception;

/**
 * La pizza solicitada no existe, o existe pero está fuera de lo que el catálogo público puede mostrar.
 *
 * <p>Un único motivo para el mismo 404, y no dos códigos, por una razón práctica: el catálogo público sólo
 * muestra pizzas activas con alguna variante disponible (RN-DS-02), así que «no existe» y «no se puede ver»
 * son la misma respuesta desde fuera. Distinguirlo confirmaría la existencia de un recurso que el
 * visitante no puede ver, que es la razón por la que en US-022 los pedidos ajenos también devuelven 404.
 *
 * <p>El identificador se incluye en el mensaje porque lo mandó el propio cliente: ayuda a depurar sin
 * filtrar nada.
 */
public class PizzaNotFoundException extends DomainException {

    public PizzaNotFoundException(long id) {
        super(ErrorCode.CATALOG_PIZZA_NOT_FOUND, "No existe ninguna pizza con el identificador " + id);
    }
}