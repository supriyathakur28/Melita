package com.melita.orders.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;

/**
 * Sends each email once per event. Claim the row (short transaction), send over SMTP with no transaction
 * open, then mark it SENT. If the service crashes between the send and the mark, a redelivery sends the email
 * again: email is at-least-once, and that rare duplicate is accepted.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final JavaMailSender mailSender;
    private final NotificationProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, JavaMailSender mailSender,
                               NotificationProperties properties, PlatformTransactionManager transactionManager,
                               Clock clock) {
        this.notifications = notifications;
        this.mailSender = mailSender;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public void send(Email email) {
        Optional<Long> claimed = claim(email);
        if (claimed.isEmpty()) {
            log.info("Email {} for event {} was already sent, skipped", email.template(), email.eventId());
            return;
        }
        mailSender.send(mime(email));
        transaction.executeWithoutResult(status ->
                notifications.findById(claimed.get()).orElseThrow().sent(clock.instant()));
        log.info("Sent {} email for order {}", email.template(), email.orderId());
    }

    /** Marks every email of a dead-lettered event as FAILED, so it shows up in the failed view. */
    public void markFailed(String eventId, String error) {
        transaction.executeWithoutResult(status ->
                notifications.findByEventId(eventId).forEach(n -> n.failed(error)));
    }

    /** @return the row to send, or empty if this email was already sent. */
    private Optional<Long> claim(Email email) {
        try {
            return transaction.execute(status -> claimOrReuse(email));
        } catch (DataIntegrityViolationException concurrentClaim) {
            return transaction.execute(status -> claimOrReuse(email));
        }
    }

    private Optional<Long> claimOrReuse(Email email) {
        Notification notification = notifications.findByEventIdAndTemplate(email.eventId(), email.template())
                .orElseGet(() -> notifications.saveAndFlush(new Notification(email.eventId(), email.template(),
                        email.orderId(), properties.agentsMailbox(), clock.instant())));
        if (notification.getStatus() == Notification.Status.SENT) {
            return Optional.empty();
        }
        notification.attempt();
        return Optional.of(notification.getId());
    }

    private MimeMessage mime(Email email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(properties.agentsMailbox());
            helper.setSubject(email.subject());
            helper.setText(email.html(), true);
            return message;
        } catch (MessagingException e) {
            throw new MailPreparationException(e);
        }
    }
}
