package com.pizzeria.domain.model;

import java.util.List;
import java.util.Objects;

public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public PageResult {
        Objects.requireNonNull(content, "content must not be null");
        content = List.copyOf(content);
        if (page < 0) {
            throw new IllegalArgumentException("Page must not be negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Page size must be greater than zero");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("Total elements must not be negative");
        }
    }

    public int totalPages() {
        if (totalElements == 0) {
            return 0;
        }
        return Math.toIntExact((totalElements - 1) / size + 1);
    }
}
