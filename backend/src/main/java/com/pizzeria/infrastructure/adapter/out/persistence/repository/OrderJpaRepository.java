package com.pizzeria.infrastructure.adapter.out.persistence.repository;

import com.pizzeria.infrastructure.adapter.out.persistence.entity.OrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {

    Optional<OrderJpaEntity> findByIdAndUserId(Long id, Long userId);

    List<OrderJpaEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<OrderJpaEntity> findAllByOrderByCreatedAtDesc();

    List<OrderJpaEntity> findByStatusOrderByCreatedAtDesc(String status);
}
