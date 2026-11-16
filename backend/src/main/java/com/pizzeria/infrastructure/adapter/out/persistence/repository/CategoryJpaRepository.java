package com.pizzeria.infrastructure.adapter.out.persistence.repository;

import com.pizzeria.infrastructure.adapter.out.persistence.entity.CategoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, Long> {

    List<CategoryJpaEntity> findByActiveTrueOrderByNameAsc();
}
