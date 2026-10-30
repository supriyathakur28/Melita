package com.melita.orders.care;

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
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Sends each email once per event. Claim the row with a lease (short transaction, atomic conditional update),
 * send over SMTP with no transaction open, then mark it SENT. Two copies of the same event processed at once:
 * only one claim succeeds, the other is skipped. If the service crashes between the send and the mark, the
 * lease expires and a redelivery sends the email again: email is at-least-once, and that rare duplicate is
 * accepted.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    /** Longer than the SMTP connect, read and write timeouts together. */
    private static final Duration LEASE = Duration.ofMinutes(2);

    private final NotificationRepository notifications;
    private final JavaMailSender mailSender;
    private final NotificationProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public NotificationService(final NotificationRepository notifications, final JavaMailSender mailSender,
                               final NotificationProperties properties,
                               final PlatformTransactionManager transactionManager, final Clock clock) {
        this.notifications = notifications;
        this.mailSender = mailSender;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public void send(final Email email) {
        final Optional<Long> claimed = claim(email);
        if (claimed.isEmpty()) {
            log.info("Email {} for event {} was already sent or is being sent, skipped", email.template(), email.eventId());
            return;
        }
        try {
            mailSender.send(mime(email));
        } catch (RuntimeException e) {
            transaction.executeWithoutResult(status ->
                    notifications.findById(claimed.get()).ifPresent(n -> n.released(e.getClass().getSimpleName())));
            throw e;
        }
        transaction.executeWithoutResult(status ->
                notifications.findById(claimed.get()).orElseThrow().sent(clock.instant()));
        log.info("Sent {} email for order {}", email.template(), email.orderId());
    }

    /** Marks every email of a dead-lettered event as FAILED, so it shows up in the failed view. */
    public void markFailed(final String eventId, final String error) {
        transaction.executeWithoutResult(status ->
                notifications.findByEventId(eventId).forEach(n -> n.failed(error)));
    }

    /** @return the row to send, or empty if this email was already sent or another consumer holds the lease. */
    private Optional<Long> claim(final Email email) {
        try {
            return transaction.execute(status -> claimOrReuse(email));
        } catch (DataIntegrityViolationException concurrentClaim) {
            return transaction.execute(status -> claimOrReuse(email));
        }
    }

    private Optional<Long> claimOrReuse(final Email email) {
        final Notification notification = notifications.findByEventIdAndTemplate(email.eventId(), email.template())
                .orElseGet(() -> notifications.saveAndFlush(new Notification(email.eventId(), email.template(),
                        email.orderId(), properties.agentsMailbox(), clock.instant())));
        final Instant now = clock.instant();
        return notifications.claim(notification.getId(), now, now.plus(LEASE)) == 1
                ? Optional.of(notification.getId())
                : Optional.empty();
    }

    private MimeMessage mime(final Email email) {
        try {
            final MimeMessage message = mailSender.createMimeMessage();
            final MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
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
