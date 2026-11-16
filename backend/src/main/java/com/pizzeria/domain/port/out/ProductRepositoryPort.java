package com.pizzeria.domain.port.out;

import com.pizzeria.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductRepositoryPort {

    Page<Product> findAll(Long categoryId, boolean onlyAvailable, Pageable pageable);

    Optional<Product> findById(Long id);

    Product save(Product product);
}
