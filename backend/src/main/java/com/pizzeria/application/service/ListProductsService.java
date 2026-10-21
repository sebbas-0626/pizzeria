package com.pizzeria.application.service;

import com.pizzeria.domain.model.Product;
import com.pizzeria.domain.port.in.ListProductsUseCase;
import com.pizzeria.domain.port.out.ProductRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ListProductsService implements ListProductsUseCase {

    private final ProductRepositoryPort productRepository;

    public ListProductsService(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public List<Product> listProducts(UUID categoryId, boolean onlyAvailable) {
        return productRepository.findAll(categoryId, onlyAvailable);
    }
}
