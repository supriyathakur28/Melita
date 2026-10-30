package com.melita.orders.fulfilment.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FulfilmentOrderRepository extends JpaRepository<FulfilmentOrder, UUID> {

    Optional<FulfilmentOrder> findByIdempotencyKey(String idempotencyKey);

    Optional<FulfilmentOrder> findByOrderId(UUID orderId);

    @EntityGraph(attributePaths = "items")
    Optional<FulfilmentOrder> findWithItemsByFulfilmentReference(String fulfilmentReference);
}
