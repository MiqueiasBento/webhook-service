package com.miqueiasbento.io.webhook_service.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.miqueiasbento.io.webhook_service.destination.Destination;
import com.miqueiasbento.io.webhook_service.destination.DestinationRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "webhook.retry.initial-wait=10ms",
        "webhook.http.request-timeout=1s"
})
class DeliveryRetryTests {
    @Autowired DestinationRepository destinations;
    @Autowired DeliveryRepository deliveries;
    @Autowired DeliveryService service;
    @Autowired ObjectMapper mapper;

    @Test
    void succeedsAfterTwoTemporaryFailures() throws Exception {
        verifyResponses(new int[] {500, 500, 200}, DeliveryStatus.DELIVERED, 3, 200);
    }

    @Test
    void failsAfterThreeServerErrors() throws Exception {
        verifyResponses(new int[] {500, 500, 500}, DeliveryStatus.FAILED, 3, 500);
    }

    @Test
    void doesNotRetryNotFound() throws Exception {
        verifyResponses(new int[] {404}, DeliveryStatus.FAILED, 1, 404);
    }

    @Test
    void retriesRequestTimeoutAndRateLimitResponses() throws Exception {
        verifyResponses(new int[] {408, 429, 200}, DeliveryStatus.DELIVERED, 3, 200);
    }

    @Test
    void retriesTimeoutAndRecordsFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer receiver = server();
        receiver.createContext("/hook", exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(2000);
                exchange.sendResponseHeaders(200, -1);
            } catch (Exception ignored) {
                // The sender has already timed out.
            } finally {
                exchange.close();
            }
        });
        receiver.start();
        try {
            Delivery delivery = service.deliver(event(destination(receiver), "timeout"));
            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(delivery.getAttemptCount()).isEqualTo(3);
            assertThat(delivery.getErrorMessage()).isNotBlank();
            assertThat(calls.get()).isEqualTo(3);
        } finally {
            receiver.stop(0);
        }
    }

    @Test
    void retriesConnectionFailure() throws Exception {
        HttpServer receiver = server();
        int port = receiver.getAddress().getPort();
        receiver.start();
        receiver.stop(0);
        Destination destination = destinations.save(
                new Destination("Unavailable", "http://127.0.0.1:" + port + "/hook"));
        Delivery delivery = service.deliver(event(destination, "connection"));
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getAttemptCount()).isEqualTo(3);
        assertThat(delivery.getResponseStatus()).isNull();
        assertThat(delivery.getErrorMessage()).isNotBlank();
    }

    @Test
    void rejectsInactiveAndMissingDestinations() throws Exception {
        Destination inactive = destinations.save(new Destination("Inactive", "http://127.0.0.1:1/hook"));
        inactive.setActive(false);
        destinations.save(inactive);
        long before = deliveries.count();
        assertThatThrownBy(() -> service.deliver(event(inactive, "inactive")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.deliver(new EventRequest(Long.MAX_VALUE, "missing", mapper.readTree("{}"))))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(deliveries.count()).isEqualTo(before);
    }

    private void verifyResponses(int[] statuses, DeliveryStatus expected, int attempts, int responseStatus)
            throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Delivery> initial = new AtomicReference<>();
        HttpServer receiver = server();
        receiver.createContext("/hook", exchange -> {
            int call = calls.getAndIncrement();
            if (call == 0) {
                initial.set(deliveries.findAll().stream()
                        .filter(d -> d.getStatus() == DeliveryStatus.PROCESSING)
                        .reduce((first, last) -> last).orElse(null));
            }
            int code = statuses[Math.min(call, statuses.length - 1)];
            byte[] body = ("response-" + call).getBytes();
            exchange.sendResponseHeaders(code, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        receiver.start();
        try {
            Delivery result = service.deliver(event(destination(receiver), "response"));
            Delivery stored = deliveries.findById(result.getId()).orElseThrow();
            assertThat(initial.get()).isNotNull();
            assertThat(initial.get().getAttemptCount()).isZero();
            assertThat(result.getStatus()).isEqualTo(expected);
            assertThat(stored.getStatus()).isEqualTo(expected);
            assertThat(stored.getAttemptCount()).isEqualTo(attempts);
            assertThat(stored.getResponseStatus()).isEqualTo(responseStatus);
            assertThat(stored.getResponseBody()).isEqualTo("response-" + (attempts - 1));
            assertThat(stored.getCreatedAt()).isNotNull();
            assertThat(stored.getCompletedAt()).isNotNull();
            if (expected == DeliveryStatus.DELIVERED) {
                assertThat(stored.getErrorMessage()).isNull();
            } else {
                assertThat(stored.getErrorMessage()).contains("HTTP " + responseStatus);
            }
            assertThat(calls.get()).isEqualTo(attempts);
        } finally {
            receiver.stop(0);
        }
    }

    private HttpServer server() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        return server;
    }

    private Destination destination(HttpServer server) {
        return destinations.save(new Destination("Test", "http://127.0.0.1:" + server.getAddress().getPort() + "/hook"));
    }

    private EventRequest event(Destination destination, String type) throws Exception {
        return new EventRequest(destination.getId(), type, mapper.readTree("{\"id\":1}"));
    }
}
