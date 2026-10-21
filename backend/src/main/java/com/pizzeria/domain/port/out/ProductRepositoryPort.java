package com.pizzeria.domain.port.out;

import com.pizzeria.domain.model.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepositoryPort {

    List<Product> findAll(UUID categoryId, boolean onlyAvailable);

    Optional<Product> findById(UUID id);

    Product save(Product product);
}
