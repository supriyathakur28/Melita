package com.melita.orders.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByEventIdAndTemplate(String eventId, String template);

    List<Notification> findByEventId(String eventId);

    List<Notification> findByOrderId(String orderId);
}
