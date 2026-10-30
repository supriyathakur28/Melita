package com.melita.orders.care;

import com.melita.orders.events.EventEnvelope;
import com.melita.orders.events.OrderAccepted;
import com.melita.orders.events.OrderEvents;
import com.melita.orders.platform.messaging.PoisonMessageException;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailComposerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final EmailComposer composer = new EmailComposer(engine(), jsonMapper);

    private static SpringTemplateEngine engine() {
        final var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        final var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    static OrderAccepted order() {
        final var address = new OrderAccepted.Address("12, Triq il-Kbira", null, "Mosta", "MST 1234");
        return new OrderAccepted(UUID.randomUUID(), "erp-partner", "ERP-42",
                new OrderAccepted.Customer("Maria", "Borg", LocalDate.of(1990, 4, 12), "maria.borg@example.com",
                        "+35679123456", new OrderAccepted.IdentityDocument("ID_CARD", "123456M"), address),
                new OrderAccepted.Installation(address, LocalDate.of(2026, 11, 9), "MORNING"),
                List.of(new OrderAccepted.Item("INTERNET", "INTERNET_1GBPS", "Internet 1Gbps")), Instant.now());
    }

    @Test
    void newOrderEmailShowsTheOrderWithTheIdNumberMasked() {
        final var event = EventEnvelope.of(OrderEvents.ORDER_ACCEPTED, "req", Instant.now(), order());

        final Email email =
                composer.compose(OrderEvents.ORDER_ACCEPTED, jsonMapper.writeValueAsBytes(event)).orElseThrow();

        assertThat(email.subject()).isEqualTo("New order ERP-42");
        assertThat(email.template()).isEqualTo("order-accepted");
        assertThat(email.eventId()).isEqualTo(event.eventId().toString());
        assertThat(email.html()).contains("Maria Borg", "Internet 1Gbps", "MST 1234", "****56M")
                .doesNotContain("123456M");
    }

    @Test
    void eventsThatNeedNoEmailAreIgnored() {
        assertThat(composer.compose(OrderEvents.ORDER_SUBMITTED, "{}".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void unreadablePayloadIsPoison() {
        assertThatThrownBy(() -> composer.compose(OrderEvents.ORDER_ACCEPTED, "nope".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PoisonMessageException.class);
    }
}
