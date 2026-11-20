package com.pizzeria.domain.port.in;

import com.pizzeria.domain.model.PageRequest;
import com.pizzeria.domain.model.PageResult;
import com.pizzeria.domain.model.Product;

public interface ListProductsUseCase {
    PageResult<Product> listProducts(Long categoryId, boolean onlyAvailable, PageRequest pageRequest);
}
