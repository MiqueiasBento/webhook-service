# Repository Guidelines

## Project Structure & Module Organization

This repository contains one Java 25 Spring Boot service. Application code lives under `src/main/java/com/miqueiasbento/io/webhook_service/`, grouped by feature in `destination/` and `delivery/`. Configuration is in `src/main/resources/application.yaml`; tests mirror the package tree under `src/test/java/`. `pom.xml` defines dependencies and the Maven build, while `docker-compose.yaml` is intended for local PostgreSQL. The planning documents are in the parent workspace's `docs/` directory and describe planned behavior, not necessarily implemented endpoints.

## Build, Test, and Development Commands

Run these from `webhook-service/` with JDK 25:

- `./mvnw compile` compiles the service.
- `./mvnw test` runs the JUnit tests.
- `./mvnw package` runs tests and builds a JAR in `target/`.
- `./mvnw spring-boot:run` starts the application after its database and environment are configured.

Run `docker compose up -d --wait` before tests or local startup. The database uses host port `5433`; `docker compose down` stops it while retaining its data volume.

## Coding Style & Naming Conventions

Use the existing Java package `com.miqueiasbento.io.webhook_service` and feature packages for related controllers, services, entities, and repositories. Name classes in `PascalCase`, methods and fields in `camelCase`, and test classes with a `Tests` suffix. Existing Java files use tabs for indentation; follow that style in nearby code. Keep YAML indentation space based. No formatter or linter is configured in `pom.xml`.

## Testing Guidelines

JUnit Jupiter is available through the Spring Boot test dependencies. Place new tests under the matching package in `src/test/java/`, and use descriptive `@Test` method names. Add focused tests for new behavior and run `./mvnw test` with PostgreSQL running before a pull request. There is no configured coverage threshold; repository and API tests check persistence and webhook delivery.

## Commit & Pull Request Guidelines

Git history contains one initial commit (`iniciando projeto webhook service`), so no established message convention exists. Use short, imperative, change specific commit subjects. In pull requests, summarize the behavior changed, list verification commands and results, link a relevant issue if one exists, and include example requests or responses when an API contract changes.

## Configuration & Secrets

Override the local connection with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` in other environments. The default `webhook` credentials are for local development only. Keep credentials and generated `target/` files out of commits.
