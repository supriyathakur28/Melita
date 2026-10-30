package com.melita.orders.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EventContractTest {

    private static final UUID ORDER_ID = UUID.fromString("3f2c8e1a-8d4b-4c71-9a55-0e6b2f1d7c90");
    private static final Instant NOW = Instant.parse("2026-10-29T10:15:30Z");

    private final JsonMapper mapper = JsonMapper.builder().build();

    static Stream<Object> payloads() {
        final var item = new OrderAccepted.Item("INTERNET", "INTERNET_1GBPS", "Internet 1Gbps");
        return Stream.of(
                sampleAccepted(),
                new OrderApprovalRequested(ORDER_ID, "ERP-1", List.of("PACKAGE:INTERNET_1GBPS"), List.of(item)),
                new OrderApproved(ORDER_ID, "ERP-1", "agent1", NOW, "checked", true),
                new OrderRejected(ORDER_ID, "ERP-1", "agent1", NOW, "Customer failed the credit check"),
                new OrderSubmitted(ORDER_ID, "ERP-1", "FUL-123", NOW),
                new OrderFulfilmentFailed(ORDER_ID, "ERP-1", 5, "HTTP_422: rejected"));
    }

    static OrderAccepted sampleAccepted() {
        final var address = new OrderAccepted.Address("12, Triq il-Kbira", null, "Mosta", "MST 1234");
        final var customer = new OrderAccepted.Customer("Maria", "Borg", LocalDate.of(1990, 4, 12),
                "maria.borg@example.com", "+35679123456",
                new OrderAccepted.IdentityDocument("ID_CARD", "123456M"), address);
        return new OrderAccepted(ORDER_ID, "erp-partner", "ERP-1", customer,
                new OrderAccepted.Installation(address, LocalDate.of(2026, 11, 9), "MORNING"),
                List.of(new OrderAccepted.Item("INTERNET", "INTERNET_1GBPS", "Internet 1Gbps")), NOW);
    }

    @ParameterizedTest
    @MethodSource("payloads")
    void everyEventSurvivesAJsonRoundTrip(final Object payload) {
        final var envelope = EventEnvelope.of("test.event", "req-1", NOW, payload);

        final String json = mapper.writeValueAsString(envelope);
        final var read = mapper.readValue(json, mapper.getTypeFactory()
                .constructParametricType(EventEnvelope.class, payload.getClass()));

        assertThat(read).isEqualTo(envelope);
    }

    @Test
    void consumersIgnoreFieldsTheyDoNotKnow() {
        final String json = """
                {"eventId":"%s","eventType":"order.submitted","schemaVersion":1,
                 "occurredAt":"2026-10-29T10:15:30Z","correlationId":"req-1","addedLater":true,
                 "data":{"orderId":"%s","externalReference":"ERP-1","fulfilmentReference":"FUL-1",
                         "submittedAt":"2026-10-29T10:15:30Z","alsoNew":{"x":1}}}
                """.formatted(UUID.randomUUID(), ORDER_ID);

        final EventEnvelope<OrderSubmitted> read = mapper.readValue(json, new TypeReference<>() {
        });

        assertThat(read.data().fulfilmentReference()).isEqualTo("FUL-1");
    }

    @Test
    void datesAreWrittenAsIsoStrings() {
        final String json = mapper.writeValueAsString(sampleAccepted());

        assertThat(json).contains("\"acceptedAt\":\"2026-10-29T10:15:30Z\"", "\"dateOfBirth\":\"1990-04-12\"");
    }

    @Test
    void onlyOrderAcceptedCarriesPersonalData() {
        payloads().map(Object::getClass)
                .filter(type -> type != OrderAccepted.class)
                .forEach(type -> assertThat(componentTypes(type))
                        .as("%s must not carry customer data", type.getSimpleName())
                        .doesNotContain(OrderAccepted.Customer.class, OrderAccepted.IdentityDocument.class,
                                OrderAccepted.Address.class));
    }

    @Test
    void routingKeysAreUnique() {
        assertThat(OrderEvents.ALL).doesNotHaveDuplicates().hasSize(6);
    }

    private static List<Class<?>> componentTypes(final Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getType).toList();
    }
}
