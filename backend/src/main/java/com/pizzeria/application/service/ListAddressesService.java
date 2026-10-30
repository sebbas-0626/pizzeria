package com.pizzeria.application.service;

import com.pizzeria.domain.model.Address;
import com.pizzeria.domain.port.in.ListAddressesUseCase;
import com.pizzeria.domain.port.out.AddressRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ListAddressesService implements ListAddressesUseCase {

    private final AddressRepositoryPort addressRepository;

    public ListAddressesService(AddressRepositoryPort addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Override
    public List<Address> listMine(Long userId) {
        return addressRepository.findByUserId(userId);
    }
}
