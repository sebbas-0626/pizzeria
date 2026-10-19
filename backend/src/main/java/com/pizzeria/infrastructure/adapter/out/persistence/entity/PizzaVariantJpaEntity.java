package com.pizzeria.infrastructure.adapter.out.persistence.entity;

import com.pizzeria.domain.model.VariantSize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
/**
 * Fila de la tabla {@code pizza_variants}.
 *
 * <p>Modelo de persistencia, no de negocio: las mismas reglas que {@link PizzaJpaEntity}.
 *
 * <p><strong>Por qué {@code pizzaId} es un {@code Long} y no una relación a {@link PizzaJpaEntity}.</strong>
 * Porque el adaptador carga pizzas y variantes con dos consultas planas y las une en memoria, y una
 * asociación de JPA obligaría a mantener el grafo de objetos en vez de dos listas. Además, un
 * {@code ManyToOne}_lazy que nadie usa es una consulta diferida esperando a que alguien la toque.
 *
 * <p><strong>Por qué {@code size} es el enumerado del dominio y no un {@code String}.</strong> La regla de
 * capa permite a la persistencia conocer {@code domain.model}, y declarar el tipo real aquí evita que el
 * mapper haga una conversión de texto en cada fila. Se serializa como {@link EnumType#STRING} para que el
 * valor persistido sea el nombre del enumerado, que es exactamente lo que acepta el
 * {@code chk_pizza_variants_size} del esquema.
 *
 * <p>El precio es {@code BigDecimal} y no {@code Money}: una entidad JPA no puede tener un campo de un tipo
 * sin constructor por defecto sin una relación personalizada, y esa relación para un importe no aporta nada.
 * El {@code NUMERIC(12,0)} de la columna es lo que garantiza la escala, y el mapper lo convierte a
 * {@link com.pizzeria.domain.model.vo.Money}, que es donde vive RN-IM-06.
 */
@Entity
@Table(name = "pizza_variants")
public class PizzaVariantJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "pizza_id", nullable = false)
    private Long pizzaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "size", nullable = false, length = 10)
    private VariantSize size;

    @Column(name = "price", nullable = false, precision = 12, scale = 0)
    private BigDecimal price;

    @Column(name = "available", nullable = false)
    private boolean available;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PizzaVariantJpaEntity() {
        // Reservado a JPA.
    }

    public Long getId() {
        return id;
    }

    public Long getPizzaId() {
        return pizzaId;
    }

    public VariantSize getSize() {
        return size;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isAvailable() {
        return available;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}