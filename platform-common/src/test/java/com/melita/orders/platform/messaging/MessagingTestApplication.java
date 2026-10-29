package com.melita.orders.platform.messaging;

import org.slf4j.MDC;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Minimal application for the messaging integration tests. Scheduling is off; tests call the relay directly. */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({MessagingSupportConfiguration.class, MessagingTestApplication.TestListener.class})
class MessagingTestApplication {

    static final String RELAY_QUEUE = "relay-test";
    static final String LISTENER_QUEUE = "listener-test";
    static final String LISTENER_ROUTING_KEY = "test.listener";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Declarables relayTestQueue() {
        var queue = QueueBuilder.durable(RELAY_QUEUE).build();
        return new Declarables(Queues.eventsExchange(), queue,
                BindingBuilder.bind(queue).to(Queues.eventsExchange()).with("order.#"));
    }

    @Bean
    Declarables listenerTestQueue() {
        return Queues.consumerQueue(LISTENER_QUEUE, List.of(LISTENER_ROUTING_KEY));
    }

    @Bean
    RecordingFailureCallback failureCallback() {
        return new RecordingFailureCallback();
    }

    static class RecordingFailureCallback implements ListenerFailureCallback {
        final List<String> deadLettered = new CopyOnWriteArrayList<>();

        @Override
        public void onDeadLetter(Message message, Throwable cause) {
            deadLettered.add(new String(message.getBody(), StandardCharsets.UTF_8));
        }
    }

    /** Behaves according to the message body: "poison", "flaky-N" (fails N times), "always-fail" or "ok". */
    static class TestListener {
        final Map<String, AtomicInteger> invocations = new ConcurrentHashMap<>();
        final Map<String, String> correlationSeen = new ConcurrentHashMap<>();
        final List<String> succeeded = new CopyOnWriteArrayList<>();

        @RabbitListener(queues = LISTENER_QUEUE)
        void onMessage(Message message) {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            int attempt = invocations.computeIfAbsent(body, b -> new AtomicInteger()).incrementAndGet();
            String requestId = MDC.get("requestId");
            if (requestId != null) {
                correlationSeen.put(body, requestId);
            }
            if (body.equals("poison")) {
                throw new PoisonMessageException("unreadable payload");
            }
            if (body.startsWith("always-fail")) {
                throw new IllegalStateException("database unavailable");
            }
            if (body.startsWith("flaky-") && attempt <= Integer.parseInt(body.substring(6))) {
                throw new IllegalStateException("temporary failure " + attempt);
            }
            succeeded.add(body);
        }
    }
}
