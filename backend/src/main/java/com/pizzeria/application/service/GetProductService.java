package com.pizzeria.application.service;

import com.pizzeria.domain.exception.ProductNotFoundException;
import com.pizzeria.domain.model.Product;
import com.pizzeria.domain.port.in.GetProductUseCase;
import com.pizzeria.domain.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetProductService implements GetProductUseCase {

    private final ProductRepositoryPort productRepository;

    @Override
    public Product get(Long productId) {
        return productRepository.findById(productId)
            .filter(Product::available)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
