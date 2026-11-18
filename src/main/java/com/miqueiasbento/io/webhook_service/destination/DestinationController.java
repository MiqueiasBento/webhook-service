package com.miqueiasbento.io.webhook_service.destination;

import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.miqueiasbento.io.webhook_service.destination.dto.DestinationRequest;

@RestController
@SecurityRequirement(name = "apiKey")
@RequiredArgsConstructor 
@RequestMapping("/api/v1/destinations")
public class DestinationController {

    private final DestinationService service;

    @PostMapping
    public ResponseEntity<Destination> create(@Valid @RequestBody DestinationRequest request) {
        Destination destination = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/destinations/" + destination.getId())).body(destination);
    }

    @GetMapping
    public List<Destination> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Destination get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public Destination update(@PathVariable Long id, @Valid @RequestBody DestinationRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public Destination setStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.setActive(id, request.active());
    }

    public record StatusRequest(@NotNull Boolean active) {}
}
