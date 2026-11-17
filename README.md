# Webhook Service

**Trabalho Prático 2 de Reuso de Software**

Serviço Spring Boot em Java 25 para entrega reutilizável de webhooks. A API cadastra destinos e envia eventos por HTTP POST de forma síncrona, com até três tentativas para falhas temporárias.

## Executar localmente

Requisitos: JDK 25 e Docker com Compose. Importe `pom.xml` na IDE como projeto Maven e selecione o JDK 25. Na raiz deste repositório:

```sh
docker compose up -d --wait
./mvnw test
./mvnw spring-boot:run
```

O PostgreSQL fica disponível em `localhost:5433`; a aplicação escuta na porta `8080`. Para parar o banco, execute `docker compose down` (preserva os dados do volume). O comando `./mvnw package` gera o JAR em `target/`.

A documentação OpenAPI fica em `http://localhost:8080/v3/api-docs` e a interface Swagger em `http://localhost:8080/swagger-ui/index.html`.

As credenciais locais padrão são `webhook`/`webhook`, para desenvolvimento. Substitua a conexão por `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD` em outros ambientes. Não use essas credenciais de exemplo em produção.

`DestinationRepositoryTests` grava e lê uma entidade `Destination` no PostgreSQL real. Os testes exigem que o container esteja ativo. O esquema é criado ou atualizado automaticamente por Hibernate (`ddl-auto: update`) nesta etapa inicial; migrações versionadas devem substituir essa opção antes de produção.

## API atual

- `POST /api/v1/destinations` cria um destino com `name` e `url`.
- `GET /api/v1/destinations` lista destinos; `GET /api/v1/destinations/{id}` consulta um.
- `PUT /api/v1/destinations/{id}` atualiza nome e URL.
- `PATCH /api/v1/destinations/{id}/status` recebe `{"active":false}` para desativar ou `true` para reativar.
- `POST /api/v1/events` recebe `destinationId`, `eventType` e `payload` JSON; devolve `deliveryId`, `status`, `attemptCount`, `responseStatus` e `errorMessage`.
- `GET /api/v1/deliveries/{id}` consulta o resultado persistido da entrega.

Exemplo de evento: `{"destinationId":1,"eventType":"order.created","payload":{"orderId":7}}`. Apenas destinos ativos recebem eventos. Uma resposta HTTP 2xx do destinatário marca a entrega como `DELIVERED`. HTTP 408, 429, 5xx e falhas de comunicação são repetidos até três tentativas, com esperas de 1 e 2 segundos. Outros erros HTTP terminam como `FAILED` sem repetição. A resposta da API é HTTP 200 com o resultado da entrega no corpo. Ainda não há autenticação ou deduplicação de eventos.

Requisições inválidas retornam `400`, recursos ausentes retornam `404` e destinos inativos retornam `409`. O corpo de erro contém `field`, `message`, `statusCode` e `error`. A entrega é gravada antes do envio com `PROCESSING` e zero tentativas; ao final, são persistidos status, tentativas realizadas, última resposta disponível, erro e horário de conclusão. O Retry envolve apenas a comunicação HTTP; as gravações no banco ocorrem fora dessa espera.
