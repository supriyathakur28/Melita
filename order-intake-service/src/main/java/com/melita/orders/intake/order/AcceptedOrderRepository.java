package com.melita.orders.intake.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AcceptedOrderRepository extends JpaRepository<AcceptedOrder, UUID> {

    /** Partner-scoped lookup: a partner can never read another partner's order. */
    Optional<AcceptedOrder> findByIdAndPartnerId(UUID id, String partnerId);

    Optional<AcceptedOrder> findByPartnerIdAndExternalReference(String partnerId, String externalReference);
}
