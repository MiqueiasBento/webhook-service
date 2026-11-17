package com.miqueiasbento.io.webhook_service.delivery;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.miqueiasbento.io.webhook_service.destination.Destination;
import com.miqueiasbento.io.webhook_service.destination.DestinationService;
import com.miqueiasbento.io.webhook_service.exception.ResourceNotFoundException;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeliveryService {
    private final DestinationService destinations;
    private final DeliveryRepository repository;
    private final WebhookHttpClient webhookHttpClient;
    private final RetryConfig retryConfig;

    public DeliveryService(DestinationService destinations, DeliveryRepository repository,
            WebhookHttpClient webhookHttpClient,
            @Value("${webhook.retry.max-attempts:3}") int maxAttempts,
            @Value("${webhook.retry.initial-wait:1s}") Duration initialWait,
            @Value("${webhook.retry.multiplier:2}") double multiplier) {
        this.destinations = destinations;
        this.repository = repository;
        this.webhookHttpClient = webhookHttpClient;
        this.retryConfig = RetryConfig.<HttpResponse<String>>custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(initialWait.toMillis(), multiplier))
                .retryOnResult(response -> isTemporary(response.statusCode()))
                .retryOnException(error -> error instanceof IOException)
                .build();
    }

    public Delivery deliver(EventRequest event) {
        Destination destination = destinations.get(event.destinationId());
        if (!destination.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Destination is inactive");
        }

        Delivery delivery = new Delivery();
        delivery.setDestination(destination);
        delivery.setEventType(event.eventType());
        delivery.setPayload(event.payload().toString());
        delivery.setStatus(DeliveryStatus.PROCESSING);
        delivery.setAttemptCount(0);
        delivery.setCreatedAt(LocalDateTime.now());
        delivery = repository.save(delivery);

        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<HttpResponse<String>> lastResponse = new AtomicReference<>();
        String payload = delivery.getPayload();
        Retry retry = Retry.of("webhookDelivery-" + delivery.getId(), retryConfig);
        try {
            HttpResponse<String> response = Retry.decorateCallable(retry, () -> {
                attempts.incrementAndGet();
                HttpResponse<String> result = webhookHttpClient.send(destination, payload);
                lastResponse.set(result);
                return result;
            }).call();
            delivery.setResponseStatus(response.statusCode());
            delivery.setResponseBody(response.body());
            delivery.setStatus(response.statusCode() >= 200 && response.statusCode() < 300
                    ? DeliveryStatus.DELIVERED : DeliveryStatus.FAILED);
            if (delivery.getStatus() == DeliveryStatus.FAILED) {
                delivery.setErrorMessage("Destination returned HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            delivery.setErrorMessage("Delivery interrupted");
            delivery.setStatus(DeliveryStatus.FAILED);
        } catch (Exception e) {
            delivery.setErrorMessage(e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName() : e.getMessage());
            delivery.setStatus(DeliveryStatus.FAILED);
        }
        if (delivery.getStatus() == DeliveryStatus.FAILED && lastResponse.get() != null
                && delivery.getResponseStatus() == null) {
            delivery.setResponseStatus(lastResponse.get().statusCode());
            delivery.setResponseBody(lastResponse.get().body());
        }
        delivery.setAttemptCount(attempts.get());
        delivery.setCompletedAt(LocalDateTime.now());
        return repository.save(delivery);
    }

    public Delivery get(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Delivery not found", "id"));
    }

    private static boolean isTemporary(int status) {
        return status == 408 || status == 429 || status >= 500 && status <= 599;
    }
}
