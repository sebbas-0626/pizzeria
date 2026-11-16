package com.pizzeria.domain.port.in;

import com.pizzeria.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListProductsUseCase {
    Page<Product> listProducts(Long categoryId, boolean onlyAvailable, Pageable pageable);
}
