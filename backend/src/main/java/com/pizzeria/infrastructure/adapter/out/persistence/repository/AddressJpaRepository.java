package com.pizzeria.infrastructure.adapter.out.persistence.repository;

import com.pizzeria.infrastructure.adapter.out.persistence.entity.AddressJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressJpaRepository extends JpaRepository<AddressJpaEntity, Long> {

    List<AddressJpaEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("select a from AddressJpaEntity a where a.id = :id and a.userId = :userId")
    Optional<AddressJpaEntity> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
