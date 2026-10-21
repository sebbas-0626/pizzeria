package com.pizzeria.infrastructure.adapter.in.web;

import com.pizzeria.domain.model.Product;
import com.pizzeria.domain.port.in.ListProductsUseCase;
import com.pizzeria.infrastructure.adapter.in.web.dto.ProductResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ListProductsUseCase listProductsUseCase;

    public ProductController(ListProductsUseCase listProductsUseCase) {
        this.listProductsUseCase = listProductsUseCase;
    }

    @GetMapping
    public List<ProductResponse> list(
        @RequestParam(required = false) UUID categoryId,
        @RequestParam(defaultValue = "false") boolean onlyAvailable
    ) {
        return listProductsUseCase.listProducts(categoryId, onlyAvailable)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
            p.id(), p.categoryId(), p.name(),
            p.description(), p.price(), p.imageUrl(), p.available()
        );
    }
}
