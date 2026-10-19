package com.pizzeria.application.dto;

/**
 * Página pedida, con los límites del contrato ya aplicados.
 *
 * <p>Vive en {@code application.dto} y <strong>no</strong> en el dominio por una razón comprobable:
 * {@code ArchitectureRulesTest.laAplicacionNoDependeDeInfrastructure} prohíbe que la capa de aplicación
 * dependa de {@code shared}. Si la paginación fuera un tipo compartido, el servicio que la usa no podría
 * compilar sin romper la regla. El dominio tampoco puede utilizarla, porque su regla le prohíbe depender de
 * nada ajeno — por eso {@code PizzaCatalogPort} habla de límite y desplazamiento en vez de páginas.
 *
 * <p>Los límites son del contrato público y por eso tienen nombre: en el código no hay un {@code 100}
 * suelto que alguien pueda cambiar sin darse cuenta de que estaba limitando el tamaño de página.
 *
 * @param pagina índice de la página, empezando en 0
 * @param tamano cuántas filas se piden por página
 */
public record PageRequest(int pagina, int tamano) {

    /** Página por defecto cuando el cliente no dice nada. */
    public static final int PAGINA_POR_DEFECTO = 0;

    /** Tamaño por defecto. Un catálogo pequeño no necesita páginas de cien pizzas. */
    public static final int TAMANO_POR_DEFECTO = 20;

    /**
     * Tamaño máximo. Sin tope, un cliente podría pedir un {@code size=100000} y la consulta traería el
     * catálogo entero de memoria.
     */
    public static final int TAMANO_MAXIMO = 100;

    /** Tamaño mínimo: pedir menos de una fila no significa nada. */
    public static final int TAMANO_MINIMO = 1;

    /**
     * Constructor verificado.
     *
     * <p>Los valores llegan validados desde el controlador, que es donde el error del cliente se traduce a
     * un 400 con el detalle. Esta comprobación es la red de seguridad para el resto del código: si alguien
     * construye una página desde dentro con un tamaño disparatado, que se entere al construct y no en una
     * consulta lenta tres meses después.
     *
     * @throws IllegalArgumentException si la página o el tamaño están fuera de rango
     */
    public PageRequest {
        if (pagina < 0) {
            throw new IllegalArgumentException("La pagina no puede ser negativa: " + pagina);
        }
        if (tamano < TAMANO_MINIMO || tamano > TAMANO_MAXIMO) {
            throw new IllegalArgumentException("El tamano de pagina debe estar entre " + TAMANO_MINIMO
                    + " y " + TAMANO_MAXIMO + ": " + tamano);
        }
    }

    /** La petición por defecto, para cuando el cliente no manda parámetros de paginación. */
    public static PageRequest porDefecto() {
        return new PageRequest(PAGINA_POR_DEFECTO, TAMANO_POR_DEFECTO);
    }

    /**
     * Cuántas filas hay que saltar para llegar a esta página.
     *
     * <p>Es el único lugar del proyecto donde se hace esta cuenta. Multiplicarla en varios sitios garantiza
     * que un día uno se olvide y el listado devuelva siempre la primera página.
     */
    public int desplazamiento() {
        return pagina * tamano;
    }

    /**
     * Cuántas páginas hay para un total de elementos dado.
     *
     * <p>Devuelve 0 y no 1 cuando el catálogo está vacío: no hay ninguna página que mostrar, y un
     * {@code totalPages: 1} con {@code content: []} contradice al cliente.
     */
    public int totalPaginas(long totalElementos) {
        if (totalElementos <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElementos / tamano);
    }
}