package com.miqueiasbento.io.webhook_service.delivery;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final DeliveryService service;

    public EventController(DeliveryService service) {
        this.service = service;
    }

    @PostMapping
    public DeliveryResponse publish(@Valid @RequestBody EventRequest request) {
        Delivery delivery = service.deliver(request);
        return new DeliveryResponse(delivery.getId(), delivery.getStatus(),
                delivery.getAttemptCount(), delivery.getResponseStatus());
    }

    public record DeliveryResponse(Long id, DeliveryStatus status, Integer attemptCount, Integer responseStatus) {}
}
