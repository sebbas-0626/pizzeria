package com.pizzeria.domain.exception;

/**
 * La dirección indicada en un pedido no existe o pertenece a otro cliente.
 *
 * <p>Sólo se le pasa a quien crea el pedido la identificación del propietario: quien llama nunca recibe
 * una respuesta distinta para «no existe» y para «es de otro cliente», porque eso permitiría averiguar
 * qué identificadores tienen direcciones ajenas.
 */
public class AddressNotFoundException extends DomainException {
    public AddressNotFoundException(Long addressId) {
        super(ErrorCode.IDENTITY_ADDRESS_NOT_FOUND,
            "Direccion no encontrada: " + addressId);
    }
}
