package com.melita.orders.management.order;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.melita.orders.management.order.OrderStatus.FULFILMENT_FAILED;
import static com.melita.orders.management.order.OrderStatus.PENDING_APPROVAL;
import static com.melita.orders.management.order.OrderStatus.READY_FOR_FULFILMENT;
import static com.melita.orders.management.order.OrderStatus.REJECTED;
import static com.melita.orders.management.order.OrderStatus.SUBMITTED;
import static org.assertj.core.api.Assertions.assertThat;

/** Every pair of states: exactly the documented transitions are allowed. */
class OrderStatusTest {

    private static final Map<OrderStatus, Set<OrderStatus>> EXPECTED = Map.of(
            PENDING_APPROVAL, Set.of(READY_FOR_FULFILMENT, REJECTED),
            READY_FOR_FULFILMENT, Set.of(SUBMITTED, FULFILMENT_FAILED),
            REJECTED, Set.of(),
            SUBMITTED, Set.of(),
            FULFILMENT_FAILED, Set.of());

    static Stream<Arguments> allPairs() {
        return Arrays.stream(OrderStatus.values())
                .flatMap(from -> Arrays.stream(OrderStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void onlyDocumentedTransitionsAreAllowed(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(EXPECTED.get(from).contains(to));
    }

    @ParameterizedTest
    @MethodSource("allPairs")
    void terminalStatesHaveNoWayOut(OrderStatus from, OrderStatus ignored) {
        assertThat(from.isTerminal()).isEqualTo(EXPECTED.get(from).isEmpty());
    }
}
