package com.pizzeria.application.service;

import com.pizzeria.domain.model.Category;
import com.pizzeria.domain.port.in.ListCategoriesUseCase;
import com.pizzeria.domain.port.out.CategoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ListCategoriesService implements ListCategoriesUseCase {

    private final CategoryRepositoryPort categoryRepository;

    @Override
    public List<Category> listActive() {
        return categoryRepository.findAllActive();
    }
}
