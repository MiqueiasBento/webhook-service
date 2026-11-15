package com.miqueiasbento.io.webhook_service.delivery;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import com.miqueiasbento.io.webhook_service.destination.Destination;
import com.miqueiasbento.io.webhook_service.destination.DestinationService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor 
public class DeliveryService {
    
    private final DestinationService destinations;
    private final DeliveryRepository repository;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

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
        delivery.setAttemptCount(1);
        delivery.setCreatedAt(LocalDateTime.now());
        delivery = repository.save(delivery);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(destination.getUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload()))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            delivery.setResponseStatus(response.statusCode());
            delivery.setResponseBody(response.body());
            delivery.setStatus(response.statusCode() >= 200 && response.statusCode() < 300
                    ? DeliveryStatus.DELIVERED : DeliveryStatus.FAILED);
        } catch (IOException | IllegalArgumentException e) {
            delivery.setErrorMessage(e.getMessage());
            delivery.setStatus(DeliveryStatus.FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            delivery.setErrorMessage("Delivery interrupted");
            delivery.setStatus(DeliveryStatus.FAILED);
        }
        delivery.setCompletedAt(LocalDateTime.now());
        return repository.save(delivery);
    }
}
