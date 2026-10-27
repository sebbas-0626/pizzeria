package com.pizzeria.domain.port.in;

import com.pizzeria.domain.model.Order;
import com.pizzeria.domain.model.PaymentMethod;
import com.pizzeria.domain.model.RequestedOrderLine;

import java.util.List;

public interface PlaceOrderUseCase {
    Order placeOrder(Long userId, Long addressId, PaymentMethod paymentMethod, String notes, List<RequestedOrderLine> items);
}
