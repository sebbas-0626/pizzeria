package com.pizzeria.infrastructure.adapter.out.persistence.repository;

import com.pizzeria.infrastructure.adapter.out.persistence.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    @Query("""
        SELECT p FROM ProductEntity p
        WHERE (:categoryId IS NULL OR p.categoryId = :categoryId)
          AND (:onlyAvailable = false OR p.available = true)
        ORDER BY p.name ASC
    """)
    List<ProductEntity> findAllByFilters(
        @Param("categoryId") Long categoryId,
        @Param("onlyAvailable") boolean onlyAvailable
    );
}
