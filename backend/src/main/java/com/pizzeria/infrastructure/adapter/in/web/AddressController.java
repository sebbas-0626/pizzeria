package com.pizzeria.infrastructure.adapter.in.web;

import com.pizzeria.domain.model.Address;
import com.pizzeria.domain.port.in.CreateAddressUseCase;
import com.pizzeria.domain.port.in.ListAddressesUseCase;
import com.pizzeria.infrastructure.adapter.in.web.dto.AddressResponse;
import com.pizzeria.infrastructure.adapter.in.web.dto.CreateAddressRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {

    private final CreateAddressUseCase createAddressUseCase;
    private final ListAddressesUseCase listAddressesUseCase;

    public AddressController(CreateAddressUseCase createAddressUseCase,
                             ListAddressesUseCase listAddressesUseCase) {
        this.createAddressUseCase = createAddressUseCase;
        this.listAddressesUseCase = listAddressesUseCase;
    }

    @PostMapping
    public ResponseEntity<AddressResponse> create(@Valid @RequestBody CreateAddressRequest request,
                                                  Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        Address address = createAddressUseCase.create(
            userId, request.alias(), request.street(), request.city(),
            request.neighborhood(), request.reference());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(address));
    }

    @GetMapping
    public List<AddressResponse> listMine(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return listAddressesUseCase.listMine(userId).stream()
            .map(this::toResponse)
            .toList();
    }

    private AddressResponse toResponse(Address address) {
        return new AddressResponse(
            address.id(), address.alias(), address.street(), address.city(),
            address.neighborhood(), address.reference(), address.createdAt());
    }
}
