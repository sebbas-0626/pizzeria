package com.pizzeria.domain.port.out;

import com.pizzeria.domain.model.Category;

import java.util.List;

public interface CategoryRepositoryPort {
    List<Category> findAllActive();
}
