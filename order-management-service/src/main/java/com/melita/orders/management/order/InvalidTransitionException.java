package com.melita.orders.management.order;

import com.melita.orders.platform.web.ApiException;
import org.springframework.http.HttpStatus;

/** An illegal status change, e.g. approving an order that was already rejected. Maps to 409. */
public class InvalidTransitionException extends ApiException {

    public InvalidTransitionException(OrderStatus from, OrderStatus to) {
        super(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
                "The order is " + from + " and cannot move to " + to);
    }
}
