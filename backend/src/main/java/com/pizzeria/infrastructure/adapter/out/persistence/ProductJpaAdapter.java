package com.pizzeria.infrastructure.adapter.out.persistence;

import com.pizzeria.domain.model.PageRequest;
import com.pizzeria.domain.model.PageResult;
import com.pizzeria.domain.model.Product;
import com.pizzeria.domain.port.out.ProductRepositoryPort;
import com.pizzeria.infrastructure.adapter.out.persistence.entity.ProductEntity;
import com.pizzeria.infrastructure.adapter.out.persistence.repository.ProductJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ProductJpaAdapter implements ProductRepositoryPort {

    private final ProductJpaRepository jpaRepository;

    public ProductJpaAdapter(ProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PageResult<Product> findAll(Long categoryId, boolean onlyAvailable, PageRequest pageRequest) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(
            pageRequest.page(),
            pageRequest.size(),
            Sort.by("name").ascending()
        );
        Page<ProductEntity> result = jpaRepository.findAllByFilters(categoryId, onlyAvailable, pageable);
        return new PageResult<>(
            result.getContent().stream().map(this::toDomain).toList(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements()
        );
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Product save(Product product) {
        ProductEntity saved = jpaRepository.save(toEntity(product));
        return toDomain(saved);
    }

    // Mapeo manual (o MapStruct si luego quieres)
    private Product toDomain(ProductEntity e) {
        return new Product(
            e.getId(), e.getCategoryId(), e.getName(),
            e.getDescription(), e.getPrice(), e.getImageUrl(), e.isAvailable()
        );
    }

    private ProductEntity toEntity(Product p) {
        ProductEntity e = new ProductEntity();
        e.setId(p.id());
        e.setCategoryId(p.categoryId());
        e.setName(p.name());
        e.setDescription(p.description());
        e.setPrice(p.price());
        e.setImageUrl(p.imageUrl());
        e.setAvailable(p.available());
        return e;
    }
}
