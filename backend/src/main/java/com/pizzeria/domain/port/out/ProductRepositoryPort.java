package com.pizzeria.domain.port.out;

import com.pizzeria.domain.model.PageRequest;
import com.pizzeria.domain.model.PageResult;
import com.pizzeria.domain.model.Product;

import java.util.Optional;

public interface ProductRepositoryPort {

    PageResult<Product> findAll(Long categoryId, boolean onlyAvailable, PageRequest pageRequest);

    Optional<Product> findById(Long id);

    Product save(Product product);
}
