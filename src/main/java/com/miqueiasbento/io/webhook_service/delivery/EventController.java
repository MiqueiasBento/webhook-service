package com.miqueiasbento.io.webhook_service.delivery;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "apiKey")
public class EventController {
    private final DeliveryService service;

    public EventController(DeliveryService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/events")
    public DeliveryResponse publish(@Valid @RequestBody EventRequest request) {
        Delivery delivery = service.deliver(request);
        return DeliveryResponse.from(delivery);
    }

    @GetMapping("/api/v1/deliveries/{id}")
    public DeliveryResponse get(@PathVariable Long id) {
        return DeliveryResponse.from(service.get(id));
    }

    public record DeliveryResponse(Long deliveryId, DeliveryStatus status, Integer attemptCount,
            Integer responseStatus, String errorMessage) {
        public static DeliveryResponse from(Delivery delivery) {
            return new DeliveryResponse(delivery.getId(), delivery.getStatus(),
                    delivery.getAttemptCount(), delivery.getResponseStatus(), delivery.getErrorMessage());
        }
    }
}
