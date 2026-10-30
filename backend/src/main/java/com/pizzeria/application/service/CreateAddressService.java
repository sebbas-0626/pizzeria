package com.pizzeria.application.service;

import com.pizzeria.domain.model.Address;
import com.pizzeria.domain.port.in.CreateAddressUseCase;
import com.pizzeria.domain.port.out.AddressRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class CreateAddressService implements CreateAddressUseCase {

    private final AddressRepositoryPort addressRepository;

    public CreateAddressService(AddressRepositoryPort addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Override
    public Address create(Long userId, String alias, String street, String city,
                          String neighborhood, String reference) {
        Address address = new Address(
            null,
            userId,
            alias.trim(),
            street.trim(),
            city.trim(),
            neighborhood == null ? null : neighborhood.trim(),
            reference == null ? null : reference.trim(),
            Instant.now()
        );
        return addressRepository.save(address);
    }
}
