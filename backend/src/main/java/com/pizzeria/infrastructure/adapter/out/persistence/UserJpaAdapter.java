package com.pizzeria.infrastructure.adapter.out.persistence;

import com.pizzeria.domain.model.User;
import com.pizzeria.domain.port.out.UserRepositoryPort;
import com.pizzeria.infrastructure.adapter.out.persistence.entity.UserJpaEntity;
import com.pizzeria.infrastructure.adapter.out.persistence.repository.UserJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserJpaAdapter implements UserRepositoryPort {

    private final UserJpaRepository jpaRepository;

    public UserJpaAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmailIgnoreCase(email).map(this::toDomain);
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = toEntity(user);
        UserJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmailIgnoreCase(email);
    }

    private User toDomain(UserJpaEntity e) {
        return new User(
            e.getId(), e.getEmail(), e.getPasswordHash(), e.getFirstName(), e.getLastName(),
            e.getPhone(), e.getRole(), e.isEnabled(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private UserJpaEntity toEntity(User u) {
        UserJpaEntity e = new UserJpaEntity();
        e.setId(u.id());
        e.setEmail(u.email());
        e.setPasswordHash(u.passwordHash());
        e.setFirstName(u.firstName());
        e.setLastName(u.lastName());
        e.setPhone(u.phone());
        e.setRole(u.role());
        e.setEnabled(u.enabled());
        e.setCreatedAt(u.createdAt());
        e.setUpdatedAt(u.updatedAt());
        return e;
    }
}