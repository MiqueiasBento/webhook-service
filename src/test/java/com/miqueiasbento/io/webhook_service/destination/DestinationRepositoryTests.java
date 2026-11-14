package com.miqueiasbento.io.webhook_service.destination;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DestinationRepositoryTests {

	@Autowired
	private DestinationRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void savesAndReadsDestination() {
		Destination saved = repository.saveAndFlush(
				new Destination("Teste", "https://example.com/webhook"));
		entityManager.clear();

		try {
			assertThat(repository.findById(saved.getId())).isPresent()
					.get().extracting(Destination::getName).isEqualTo("Teste");
		} finally {
			repository.deleteById(saved.getId());
		}
	}
}
