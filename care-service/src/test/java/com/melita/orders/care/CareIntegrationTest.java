package com.melita.orders.care;

import com.melita.orders.events.EventEnvelope;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * The real service against MySQL, RabbitMQ and Mailpit (a real SMTP server with an HTTP API). Each test class
 * gets its own context, closed afterwards, so a cached context's listener cannot consume another class's messages.
 */
@SpringBootTest
@DirtiesContext
public abstract class CareIntegrationTest {

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("care");

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management");

    static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27"))
            .withExposedPorts(1025, 8025);

    static {
        Startables.deepStart(MYSQL, RABBIT, MAILPIT).join();
    }

    /** Subclasses choose the SMTP server: Mailpit, or an unreachable port. */
    protected static void useMailpit(final DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    @Autowired
    protected RabbitTemplate rabbitTemplate;
    @Autowired
    protected JsonMapper jsonMapper;

    protected void publish(final String eventType, final EventEnvelope<?> event) {
        rabbitTemplate.send("orders.events", eventType, MessageBuilder.withBody(jsonMapper.writeValueAsBytes(event))
                .setMessageId(event.eventId().toString()).setType(eventType).build());
    }

    /** Subjects and recipients of the emails Mailpit received. */
    protected List<String> mailbox() {
        final JsonNode response = jsonMapper.readTree(RestClient.create().get()
                .uri("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + "/api/v1/messages?limit=200")
                .retrieve().body(String.class));
        final List<String> mails = new ArrayList<>();
        for (final JsonNode message : response.get("messages")) {
            mails.add(message.get("Subject").asString() + " -> " + message.get("To").get(0).get("Address").asString());
        }
        return mails;
    }
}
