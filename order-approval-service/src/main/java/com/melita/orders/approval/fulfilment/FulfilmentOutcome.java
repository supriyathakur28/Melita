package com.melita.orders.approval.fulfilment;

/** What a submission attempt achieved; decides the order's next state. */
public sealed interface FulfilmentOutcome {

    /** 2xx: Fulfilment has the order (201 created, or 200 replay of an earlier attempt). */
    record Submitted(String fulfilmentReference) implements FulfilmentOutcome {
    }

    /** 429, 5xx, timeout or connection error: the outcome may be unknown, so retry with the same key. */
    record RetryLater(String error) implements FulfilmentOutcome {
    }

    /** 4xx (except 429): Fulfilment refused the order; retrying cannot help. */
    record Rejected(String error) implements FulfilmentOutcome {
    }

    /** The circuit breaker is open: no call was made, so no attempt is used up. */
    record NotAttempted() implements FulfilmentOutcome {
    }
}
