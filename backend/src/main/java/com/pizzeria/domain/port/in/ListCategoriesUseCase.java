package com.pizzeria.domain.port.in;

import com.pizzeria.domain.model.Category;

import java.util.List;

/**
 * Use case interface for listing active categories in the pizzeria domain.
 */
public interface ListCategoriesUseCase {
    List<Category> listActive();
}
