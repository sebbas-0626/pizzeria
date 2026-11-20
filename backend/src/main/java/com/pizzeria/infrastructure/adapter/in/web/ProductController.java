package com.pizzeria.infrastructure.adapter.in.web;

import com.pizzeria.domain.model.PageRequest;
import com.pizzeria.domain.model.PageResult;
import com.pizzeria.domain.model.Product;
import com.pizzeria.domain.port.in.GetProductUseCase;
import com.pizzeria.domain.port.in.ListProductsUseCase;
import com.pizzeria.infrastructure.adapter.in.web.dto.PageResponse;
import com.pizzeria.infrastructure.adapter.in.web.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ListProductsUseCase listProductsUseCase;
    private final GetProductUseCase getProductUseCase;

    @GetMapping
    public PageResponse<ProductResponse> list(
        @RequestParam(required = false) Long categoryId,
        @RequestParam(defaultValue = "true") boolean onlyAvailable,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageResult<Product> result = listProductsUseCase.listProducts(
            categoryId,
            onlyAvailable,
            new PageRequest(page, size)
        );
        return new PageResponse<>(
            result.content().stream().map(this::toResponse).toList(),
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages()
        );
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> get(@PathVariable Long productId) {
        return ResponseEntity.ok(toResponse(getProductUseCase.get(productId)));
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(p.id(), p.categoryId(), p.name(), p.description(), p.price(), p.imageUrl(), p.available());
    }
}
