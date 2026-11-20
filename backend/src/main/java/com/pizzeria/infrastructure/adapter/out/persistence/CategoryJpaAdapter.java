package com.pizzeria.infrastructure.adapter.out.persistence;

import com.pizzeria.domain.model.Category;
import com.pizzeria.domain.port.out.CategoryRepositoryPort;
import com.pizzeria.infrastructure.adapter.out.persistence.entity.CategoryJpaEntity;
import com.pizzeria.infrastructure.adapter.out.persistence.repository.CategoryJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CategoryJpaAdapter implements CategoryRepositoryPort {

    private final CategoryJpaRepository jpaRepository;

    public CategoryJpaAdapter(CategoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Category> findAllActive() {
        return jpaRepository.findByActiveTrueOrderByNameAsc().stream()
            .map(this::toDomain)
            .toList();
    }

    private Category toDomain(CategoryJpaEntity e) {
        return new Category(e.getId(), e.getName(), e.getDescription(), e.isActive());
    }
}
