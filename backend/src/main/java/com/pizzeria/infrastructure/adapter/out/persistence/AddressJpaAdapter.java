package com.pizzeria.infrastructure.adapter.out.persistence;

import com.pizzeria.domain.model.Address;
import com.pizzeria.domain.port.out.AddressRepositoryPort;
import com.pizzeria.infrastructure.adapter.out.persistence.entity.AddressJpaEntity;
import com.pizzeria.infrastructure.adapter.out.persistence.repository.AddressJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class AddressJpaAdapter implements AddressRepositoryPort {

    private final AddressJpaRepository jpaRepository;

    public AddressJpaAdapter(AddressJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Address save(Address address) {
        AddressJpaEntity saved = jpaRepository.save(toEntity(address));
        return toDomain(saved);
    }

    @Override
    public List<Address> findByUserId(Long userId) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<Address> findByIdAndUserId(Long id, Long userId) {
        return jpaRepository.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    private Address toDomain(AddressJpaEntity e) {
        return new Address(
            e.getId(), e.getUserId(), e.getAlias(), e.getStreet(), e.getCity(),
            e.getNeighborhood(), e.getReference(), e.getCreatedAt());
    }

    private AddressJpaEntity toEntity(Address a) {
        AddressJpaEntity e = new AddressJpaEntity();
        e.setId(a.id());
        e.setUserId(a.userId());
        e.setAlias(a.alias());
        e.setStreet(a.street());
        e.setCity(a.city());
        e.setNeighborhood(a.neighborhood());
        e.setReference(a.reference());
        e.setCreatedAt(a.createdAt());
        // updated_at tampoco se mapea: no hay edición de direcciones y la columna tiene DEFAULT now().
        return e;
    }
}
