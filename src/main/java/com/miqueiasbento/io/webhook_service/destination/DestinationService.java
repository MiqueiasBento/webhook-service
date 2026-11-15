package com.miqueiasbento.io.webhook_service.destination;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.miqueiasbento.io.webhook_service.destination.dto.DestinationRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor 
public class DestinationService {

    private final DestinationRepository repository;

    @Transactional
    public Destination create(DestinationRequest request) {
        return repository.save(new Destination(request.name(), request.url()));
    }

    @Transactional(readOnly = true)
    public List<Destination> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Destination get(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Destination not found"));
    }

    @Transactional
    public Destination update(Long id, DestinationRequest request) {
        Destination destination = get(id);
        destination.setName(request.name());
        destination.setUrl(request.url());
        return repository.save(destination);
    }

    @Transactional
    public Destination setActive(Long id, boolean active) {
        Destination destination = get(id);
        destination.setActive(active);
        return repository.save(destination);
    }
}
