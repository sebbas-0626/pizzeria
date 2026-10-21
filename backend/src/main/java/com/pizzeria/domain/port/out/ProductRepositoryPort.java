package com.pizzeria.domain.port.out;

import com.pizzeria.domain.model.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepositoryPort {

    List<Product> findAll(Long categoryId, boolean onlyAvailable);

    Optional<Product> findById(Long id);

    Product save(Product product);
}
