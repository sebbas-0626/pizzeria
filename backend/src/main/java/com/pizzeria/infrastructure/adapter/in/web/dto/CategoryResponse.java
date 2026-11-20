package com.pizzeria.infrastructure.adapter.in.web.dto;

public record CategoryResponse(
    Long id,
    String name,
    String description
) {}
