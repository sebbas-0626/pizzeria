package com.pizzeria.infrastructure.adapter.in.web;

import com.pizzeria.domain.model.Product;
import com.pizzeria.domain.port.in.GetProductUseCase;
import com.pizzeria.domain.port.in.ListProductsUseCase;
import com.pizzeria.infrastructure.adapter.in.web.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ListProductsUseCase listProductsUseCase;
    private final GetProductUseCase getProductUseCase;

    @GetMapping
    public Page<ProductResponse> list(
        @RequestParam(required = false) Long categoryId,
        @RequestParam(defaultValue = "true") boolean onlyAvailable,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return listProductsUseCase.listProducts(categoryId, onlyAvailable, pageable)
            .map(this::toResponse);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> get(@PathVariable Long productId) {
        return ResponseEntity.ok(toResponse(getProductUseCase.get(productId)));
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(p.id(), p.categoryId(), p.name(), p.description(), p.price(), p.imageUrl(), p.available());
    }
}
