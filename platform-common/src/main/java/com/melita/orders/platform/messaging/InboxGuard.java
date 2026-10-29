package com.melita.orders.platform.messaging;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;

/**
 * Idempotent receiver. Records a message ID in {@code processed_message} inside the consumer's business
 * transaction: the first delivery gets {@code true}, any redelivery gets {@code false}.
 * <p>
 * If two instances race on the same message, the second insert waits on the first one's row lock and then
 * fails on the primary key, so exactly one of them proceeds. In MySQL a failed statement does not abort the
 * surrounding transaction, so the caller can simply skip the message.
 */
public class InboxGuard {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public InboxGuard(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(String messageId) {
        try {
            jdbc.update("insert into processed_message (message_id, processed_at) values (?, ?)",
                    messageId, Timestamp.from(clock.instant()));
            return true;
        } catch (DuplicateKeyException alreadyProcessed) {
            return false;
        }
    }
}
