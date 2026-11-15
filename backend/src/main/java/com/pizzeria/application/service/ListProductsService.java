package com.pizzeria.application.service;

import com.pizzeria.domain.model.Product;
import com.pizzeria.domain.port.in.ListProductsUseCase;
import com.pizzeria.domain.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ListProductsService implements ListProductsUseCase {

    private final ProductRepositoryPort productRepository;

    @Override
    public List<Product> listProducts(Long categoryId, boolean onlyAvailable) {
        return productRepository.findAll(categoryId, onlyAvailable);
    }
}
