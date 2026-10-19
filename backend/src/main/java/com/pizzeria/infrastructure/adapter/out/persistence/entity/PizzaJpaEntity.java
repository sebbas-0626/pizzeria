package com.pizzeria.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
/**
 * Fila de la tabla {@code pizzas}.
 *
 * <p>Es un modelo de <strong>persistencia</strong>, no el modelo de negocio (ADR-0003). No tiene invariantes,
 * no lanza excepciones de negocio y no se expone fuera de {@code infrastructure.adapter.out.persistence}: el
 * dominio ve {@link com.pizzeria.domain.model.Pizza}, y entre medias hay un mapper. La razón de tener los
 * dos es que las reglas del catálogo cambian con el negocio y el esquema tiene que seguir siendo estable.
 *
 * <p><strong>Por qué la colección de variantes NO está mapeada.</strong> Podría ser
 * {@code @OneToMany(mappedBy = "pizza")}, pero el adaptador carga las variantes con una consulta aparte y
 * explícita, en un número fijo de sentencias. Dejar la colección sin mapear hace que sea <em>imposible</em>
 * disparar por accidente el N+1 de una carga perezosa: si alguien más adelante añadiera el
 * {@code OneToMany} «sólo por comodidad», el catálogo volvería a hacer una consulta por pizza sin que nada
 * avise. Está sin mapear a propósito.
 *
 * <p>Los identificadores los genera la aplicación, no la base, así que la columna {@code id} no tiene
 * {@code DEFAULT} y la entidad no tiene {@code @GeneratedValue}. Ver {@code db/migration/README.md} §4.
 *
 * <p>Los tipos y longitudes coinciden con {@code V1__baseline_schema.sql} porque {@code ddl-auto=validate}
 * compara la entidad con el esquema real en cada arranque: una columna mal declarada impide que la
 * aplicación levante.
 */
@Entity
@Table(name = "pizzas")
public class PizzaJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "image_key", length = 500)
    private String imageKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PizzaJpaEntity() {
        // Reservado a JPA. La construcción real la hace el mapper, que es quien conoce las columnas.
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public String getImageKey() {
        return imageKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}