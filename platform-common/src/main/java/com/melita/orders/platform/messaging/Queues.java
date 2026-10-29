package com.melita.orders.platform.messaging;

import com.melita.orders.events.OrderEvents;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

import java.util.ArrayList;
import java.util.List;

/** Declarations shared by producers and consumers. Declaring is idempotent, so start-up order does not matter. */
public final class Queues {

    private Queues() {
    }

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
    public static Declarables consumerQueue(String queueName, List<String> routingKeys) {
        TopicExchange events = eventsExchange();
        DirectExchange deadLetters = deadLetterExchange();
        String dlqName = queueName + ".dlq";

        Queue queue = QueueBuilder.durable(queueName).quorum()
                .deadLetterExchange(OrderEvents.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(dlqName)
                .build();
        Queue dlq = QueueBuilder.durable(dlqName).quorum().build();

        List<Declarable> declarables = new ArrayList<>(List.of(events, deadLetters, queue, dlq,
                BindingBuilder.bind(dlq).to(deadLetters).with(dlqName)));
        for (String routingKey : routingKeys) {
            Binding binding = BindingBuilder.bind(queue).to(events).with(routingKey);
            declarables.add(binding);
        }
        return new Declarables(declarables);
    }
}
