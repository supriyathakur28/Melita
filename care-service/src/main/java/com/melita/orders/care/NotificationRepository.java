package com.melita.orders.care;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByEventIdAndTemplate(String eventId, String template);

    List<Notification> findByEventId(String eventId);

    List<Notification> findByOrderId(String orderId);

    /**
     * Atomic claim: succeeds (returns 1) for one caller only, when the row is PENDING or FAILED, or when another
     * consumer's lease expired (it crashed mid-send).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
               set n.status = com.melita.orders.care.Notification.Status.SENDING,
                   n.claimedUntil = :leaseUntil, n.attempts = n.attempts + 1
             where n.id = :id
               and (n.status in (com.melita.orders.care.Notification.Status.PENDING,
                                 com.melita.orders.care.Notification.Status.FAILED)
                    or (n.status = com.melita.orders.care.Notification.Status.SENDING
                        and n.claimedUntil < :now))
            """)
    int claim(Long id, Instant now, Instant leaseUntil);
}
