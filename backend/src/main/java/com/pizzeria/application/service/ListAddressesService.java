package com.pizzeria.application.service;

import com.pizzeria.domain.model.Address;
import com.pizzeria.domain.port.in.ListAddressesUseCase;
import com.pizzeria.domain.port.out.AddressRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ListAddressesService implements ListAddressesUseCase {

    private final AddressRepositoryPort addressRepository;

    @Override
    public List<Address> listMine(Long userId) {
        return addressRepository.findByUserId(userId);
    }
}
