package com.pizzeria.domain.port.out;

import com.pizzeria.domain.model.Pizza;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para leer el catálogo de pizzas.
 *
 * <p>Es un puerto porque las consultas usan una base de datos relacional y esa es una decisión de
 * infraestructura que puede cambiar sin que el dominio se entere (AGENTS.md §3.3, preguntas 2 y 3). El
 * nombre del módulo desaparece de los paquetes con ADR-0004, pero <strong>no</strong> desaparece de los
 * códigos de error ni de este nombre: {@code CATALOG_…} es parte del contrato público.
 *
 * <p><strong>Por qué el puerto habla de límite y desplazamiento, y no de páginas.</strong> El puerto vive
 * en el dominio, y {@code ArchitectureRulesTest.elDominioNoDependeDeFrameworks} impide que el dominio
 * dependa de nada ajeno. Los tipos de paginación que construyen la respuesta HTTP son de la capa de
 * aplicación, así que el puerto no puede nombrarlos sin romper la regla. Hablar de límite y desplazamiento
 * es, además, la forma menos ambigua posible: el caso de uso decide cuántas filas pide, y el
 * adaptador decide cómo las trae.
 *
 * <p>El coste de esta decisión es que el contenido y el total se piden en dos llamadas. Se pide a
 * propósito, porque ambas se ejecutan dentro de la misma transacción de sólo lectura y la alternativa
 * —un tipo {@code Page} compartido en el dominio— metería en el núcleo un concepto que no es de negocio.
 *
 * <p>El filtro {@code soloVariantesDisponibles} no es un detalle de implementación: implementa RN-DS-02,
 * que una pizza sin ninguna variante disponible no aparece en el catálogo público.
 */
public interface PizzaCatalogPort {

    /**
     * Una pizza por su identificador, con sus variantes, o vacío si no se puede mostrar.
     *
     * <p>Devuelve vacío tanto si la pizza no existe como si está inactiva o se le han retirado todas las
     * variantes: las tres cosas son un 404 para quien consulta. Ver {@code PizzaNotFoundException}.
     *
     * @param id identificador de la pizza. El catálogo usa BIGINT desde la migración V2: se teclea a mano
     *           para probar la API, así que un número se lee y se comprueba mucho mejor que un UUID
     */
    Optional<Pizza> buscarPorId(long id);

    /**
     * Las pizzas de una ventana del catálogo, con sus variantes ya cargadas.
     *
     * <p><strong>Contrato de número de consultas:</strong> el número de sentencias SQL no depende del número
     * de pizzas devueltas. Cargar aquí un objeto por pizza parece más simple y es un N+1 esperando a que
     * haya datos reales; la prueba de integración cuenta las sentencias precisamente para que no vuelva.
     *
     * @param limite                   máximo de pizzas a devolver; siempre mayor que cero
     * @param desplazamiento           cuántas pizzas saltar; siempre mayor o igual que cero
     * @param soloVariantesDisponibles si es {@code true}, sólo pizzas activas con al menos una variante
     *                                 disponible y sólo esas variantes (RN-DS-02)
     */
    List<Pizza> listar(int limite, int desplazamiento, boolean soloVariantesDisponibles);

    /**
     * Cuántas pizzas hay en total con el mismo criterio de filtro, para calcular las páginas.
     *
     * <p>Debe aplicar <strong>exactamente</strong> el mismo filtro que {@link #listar}, o el total
     * describiría un catálogo que no es el que se devuelve.
     */
    long contar(boolean soloVariantesDisponibles);
}