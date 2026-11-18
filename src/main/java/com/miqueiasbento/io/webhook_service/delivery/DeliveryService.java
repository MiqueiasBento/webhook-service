package com.miqueiasbento.io.webhook_service.delivery;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.miqueiasbento.io.webhook_service.destination.Destination;
import com.miqueiasbento.io.webhook_service.destination.DestinationService;
import com.miqueiasbento.io.webhook_service.destination.DestinationUrlPolicy;
import com.miqueiasbento.io.webhook_service.exception.ResourceNotFoundException;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeliveryService {
    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private final DestinationService destinations;
    private final DeliveryRepository repository;
    private final WebhookHttpClient webhookHttpClient;
    private final DestinationUrlPolicy urlPolicy;
    private final RetryConfig retryConfig;

    public DeliveryService(DestinationService destinations, DeliveryRepository repository,
            WebhookHttpClient webhookHttpClient, DestinationUrlPolicy urlPolicy,
            @Value("${webhook.retry.max-attempts:3}") int maxAttempts,
            @Value("${webhook.retry.initial-wait:1s}") Duration initialWait,
            @Value("${webhook.retry.multiplier:2}") double multiplier) {
        this.destinations = destinations;
        this.repository = repository;
        this.webhookHttpClient = webhookHttpClient;
        this.urlPolicy = urlPolicy;
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
        urlPolicy.validate(destination.getUrl());

        Delivery delivery = new Delivery();
        delivery.setDestination(destination);
        delivery.setEventType(event.eventType());
        delivery.setPayload(event.payload().toString());
        delivery.setStatus(DeliveryStatus.PROCESSING);
        delivery.setAttemptCount(0);
        delivery.setCreatedAt(LocalDateTime.now());
        delivery = repository.save(delivery);
        long startedNanos = System.nanoTime();
        Long deliveryId = delivery.getId();
        log.info("Delivery started: deliveryId={}, destinationId={}", deliveryId, destination.getId());

        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<HttpResponse<String>> lastResponse = new AtomicReference<>();
        String payload = delivery.getPayload();
        Retry retry = Retry.of("webhookDelivery-" + delivery.getId(), retryConfig);
        try {
            HttpResponse<String> response = Retry.decorateCallable(retry, () -> {
                urlPolicy.validate(destination.getUrl());
                int attempt = attempts.incrementAndGet();
                try {
                    HttpResponse<String> result = webhookHttpClient.send(destination, payload);
                    lastResponse.set(result);
                    log.info("Delivery attempt: deliveryId={}, attempt={}, responseStatus={}",
                            deliveryId, attempt, result.statusCode());
                    return result;
                } catch (IOException e) {
                    log.warn("Delivery attempt failed: deliveryId={}, attempt={}, cause={}",
                            deliveryId, attempt, e.getClass().getSimpleName());
                    throw e;
                }
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
        } catch (ResponseBodyTooLargeException e) {
            delivery.setResponseStatus(e.getResponseStatus());
            delivery.setErrorMessage(e.getMessage());
            delivery.setStatus(DeliveryStatus.FAILED);
        } catch (Exception e) {
            delivery.setErrorMessage("Delivery failed: " + e.getClass().getSimpleName());
            delivery.setStatus(DeliveryStatus.FAILED);
        }
        if (delivery.getStatus() == DeliveryStatus.FAILED && lastResponse.get() != null
                && delivery.getResponseStatus() == null) {
            delivery.setResponseStatus(lastResponse.get().statusCode());
            delivery.setResponseBody(lastResponse.get().body());
        }
        delivery.setAttemptCount(attempts.get());
        delivery.setCompletedAt(LocalDateTime.now());
        Delivery finished = repository.save(delivery);
        log.info("Delivery completed: deliveryId={}, status={}, attempts={}, durationMs={}",
                deliveryId, finished.getStatus(), attempts.get(),
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
        return finished;
    }

    public Delivery get(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Delivery not found", "id"));
    }

    private static boolean isTemporary(int status) {
        return status == 408 || status == 429 || status >= 500 && status <= 599;
    }
}
