package com.melita.orders.approval;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Plays both the Keycloak token endpoint and the Fulfilment API, on one local port. Responses for an order
 * can be scripted (e.g. 503 then 201); requests are recorded for assertions.
 */
public final class FakeFulfilment {

    public record Received(String idempotencyKey, String authorization, String body) {
    }

    private final HttpServer server;
    private final Map<String, Queue<Integer>> scripted = new ConcurrentHashMap<>();
    private final List<Received> received = new CopyOnWriteArrayList<>();

    public FakeFulfilment() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/token", exchange -> respond(exchange, 200,
                "{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":300}"));
        server.createContext("/api/v1/fulfilment-orders", this::fulfilment);
        server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** The next calls for this order answer with these statuses, in order; afterwards 201. */
    public void script(final String orderId, final Integer... statuses) {
        scripted.put(orderId, new ConcurrentLinkedQueue<>(List.of(statuses)));
    }

    public List<Received> receivedFor(final String orderId) {
        return received.stream().filter(r -> orderId.equals(r.idempotencyKey())).toList();
    }

    private void fulfilment(final HttpExchange exchange) throws IOException {
        final String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received.add(new Received(key, exchange.getRequestHeaders().getFirst("Authorization"), body));
        final Queue<Integer> statuses = scripted.get(key);
        final Integer status = statuses == null ? null : statuses.poll();
        if (status != null) {
            respond(exchange, status, "{\"title\":\"scripted\"}");
        } else {
            respond(exchange, 201, "{\"fulfilmentReference\":\"FUL-" + key.substring(0, 8) + "\",\"status\":\"SCHEDULED\"}");
        }
    }

    private static void respond(final HttpExchange exchange, final int status, final String json) throws IOException {
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
