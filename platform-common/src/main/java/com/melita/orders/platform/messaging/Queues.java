package com.melita.orders.platform.messaging;

import com.melita.orders.events.OrderEvents;
import lombok.experimental.UtilityClass;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

import java.util.List;
import java.util.stream.Stream;

/** Declarations shared by producers and consumers. Declaring is idempotent, so start-up order does not matter. */
@UtilityClass
public class Queues {

    public static TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(OrderEvents.EXCHANGE).durable(true).build();
    }

    public static DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(OrderEvents.DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    /**
     * A durable quorum queue bound to the events exchange, plus its dead-letter queue. The dead-letter routing
     * key is set explicitly: without it, a dead-lettered message keeps its original routing key and matches no
     * binding on the dead-letter exchange.
     */
    public static Declarables consumerQueue(final String queueName, final List<String> routingKeys) {
        final TopicExchange events = eventsExchange();
        final DirectExchange deadLetters = deadLetterExchange();
        final String dlqName = queueName + ".dlq";

        final Queue queue = QueueBuilder.durable(queueName).quorum()
                .deadLetterExchange(OrderEvents.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(dlqName)
                .build();
        final Queue dlq = QueueBuilder.durable(dlqName).quorum().build();

        final Stream<Declarable> infrastructure = Stream.of(events, deadLetters, queue, dlq,
                BindingBuilder.bind(dlq).to(deadLetters).with(dlqName));
        final Stream<Declarable> bindings = routingKeys.stream()
                .map(routingKey -> BindingBuilder.bind(queue).to(events).with(routingKey));
        return new Declarables(Stream.concat(infrastructure, bindings).toList());
    }
}
