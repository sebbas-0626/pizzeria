package com.pizzeria.infrastructure.adapter.out.persistence.mapper;

import com.pizzeria.domain.model.PizzaVariant;
import com.pizzeria.domain.model.vo.Money;
import com.pizzeria.infrastructure.adapter.out.persistence.entity.PizzaVariantJpaEntity;

import java.util.Comparator;
import java.util.List;

/**
 * Convierte filas de {@code pizza_variants} en variantes del dominio.
 *
 * <p>Existe porque la entidad JPA y el modelo de dominio son dos cosas distintas (ADR-0003). El mapper es la
 * única frontera donde se tocan, así que es también el único sitio donde puede estar el conocimiento de que
 * {@code NUMERIC(12,0)} se convierte en {@link Money}.
 *
 * <p>El precio se convierte con {@link Money#of}, que rechaza los decimales. Si algún día una fila tuviera
 * un importe fraccionario —porque alguien cambiase la columna o escribiese un {@code INSERT} a mano—, la
 * conversión fallaría al leer el catálogo en lugar de propagar un importe redondeado hasta un pedido. Es una
 * defensa, no un corrector.
 *
 * <p>El orden por tamaño se aplica aquí y no en la consulta: {@code order by} sobre el enumerado ordenaría
 * alfabéticamente, que es el orden contrario al que espera quien elige un tamaño. Ver
 * {@link com.pizzeria.domain.model.VariantSize#orden()}.
 */
public final class PizzaVariantMapper {

    private PizzaVariantMapper() {
        // Clase de utilidad: sin estado y sin instancias.
    }

/** Variantes del dominio en orden de tamaño presentable: SMALL, MEDIUM, LARGE. */
public static List<PizzaVariant> aDominio(List<PizzaVariantJpaEntity> entidades) {
        return ordenarPorTamano(entidades.stream().map(PizzaVariantMapper::aDominio).toList());
    }

    /**
     * Las ordena de menor a mayor tamaño.
     *
     * <p>Es público y no privado a propósito: el adaptador agrupa las variantes por pizza <em>después</em> de
     * convertirlas, y si el orden sólo se aplicara en la conversión el listado devolvería las variantes en
     * el orden que traiga la base —que no es ninguno—. Ordenar aquí es lo que hace que el orden del
     * catálogo sea el mismo venga la consulta de donde venga.
     */
    public static List<PizzaVariant> ordenarPorTamano(List<PizzaVariant> variantes) {
        return variantes.stream()
                .sorted(Comparator.comparingInt(variante -> variante.size().orden()))
                .toList();
    }

    public static PizzaVariant aDominio(PizzaVariantJpaEntity entidad) {
        return PizzaVariant.reconstituir(
                entidad.getId(),
                entidad.getPizzaId(),
                entidad.getSize(),
                Money.of(entidad.getPrice()),
                entidad.isAvailable(),
                entidad.getCreatedAt(),
                entidad.getUpdatedAt());
    }
}