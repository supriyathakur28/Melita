package com.melita.orders.approval.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    /** Summary columns only (no items, no personal data) for the agents' list. */
    interface OrderSummaryView {
        UUID getId();

        String getPartnerId();

        String getExternalReference();

        OrderStatus getStatus();

        boolean isApprovalRequired();

        Instant getReceivedAt();
    }

    Page<OrderSummaryView> findByStatus(OrderStatus status, Pageable pageable);

    Page<OrderSummaryView> findAllProjectedBy(Pageable pageable);

    /** Detail view: items in the same query. */
    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findWithItemsById(UUID id);

    /**
     * {@code SELECT ... FOR UPDATE}: used before changing an order. Without it, two concurrent decisions
     * deadlock in MySQL: each history insert takes a shared lock on the order row (foreign key check), then
     * each update waits for the other's shared lock. With it, the second caller waits, then sees the new status.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> findForUpdate(UUID id);

    /** Uses the (status, next_attempt_at) index. */
    @Query("""
            select new com.melita.orders.approval.order.DueOrder(o.id, o.correlationId) from CustomerOrder o
            where o.status = com.melita.orders.approval.order.OrderStatus.READY_FOR_FULFILMENT
              and o.nextAttemptAt <= :now
            order by o.nextAttemptAt
            limit 20
            """)
    List<DueOrder> findDueForSubmission(Instant now);
}
