package com.pizzeria.infrastructure.adapter.in.web;

import com.pizzeria.domain.port.in.ListCategoriesUseCase;
import com.pizzeria.infrastructure.adapter.in.web.dto.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
// endpoint base path: /api/v1/categories, su funcion es listar todas las categorias activas de la pizzeria
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final ListCategoriesUseCase listCategoriesUseCase;

    @GetMapping
    public List<CategoryResponse> list() {
        return listCategoriesUseCase.listActive().stream()
            .map(c -> new CategoryResponse(c.id(), c.name(), c.description()))
            .toList();
    }
}
