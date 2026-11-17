# Webhook Service

**Trabalho Prático 2 de Reuso de Software**

Serviço Spring Boot em Java 25 para entrega reutilizável de webhooks. A API cadastra destinos e envia eventos por HTTP POST de forma síncrona, com até três tentativas para falhas temporárias.

## Instalação e execução local

Requisitos: JDK 25 e Docker com Compose. Importe `pom.xml` na IDE como projeto Maven e selecione o JDK 25. Na raiz deste repositório:

```sh
docker compose up -d --wait
./mvnw test
./mvnw spring-boot:run
```

O PostgreSQL fica disponível em `localhost:5433`; a aplicação escuta na porta `8080`. Para parar o banco, execute `docker compose down` (preserva os dados do volume). O comando `./mvnw package` gera o JAR em `target/`. Os testes usam o PostgreSQL real e exigem que ele esteja ativo.

## Execução completa no Docker

Com Docker e Compose, sem instalar JDK localmente, execute `docker compose --profile full up -d --build --wait`. O perfil `full` compila e inicia a aplicação após o PostgreSQL ficar saudável. Acesse a API em `http://localhost:8080`. Use `docker compose --profile full logs -f app` para acompanhar a inicialização e `docker compose --profile full down` para parar os serviços. O volume do banco é preservado; `down -v` também remove os dados.

O perfil padrão (`docker compose up -d --wait`) inicia somente o banco, para desenvolvimento com Maven ou IDE. Não execute simultaneamente a aplicação local e o perfil `full` na mesma porta `8080`.

## Configuração

| Variável | Padrão local | Uso |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/webhook_service` | Conexão JDBC |
| `SPRING_DATASOURCE_USERNAME` | `webhook` | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | `webhook` | Senha do banco |

`application.yaml` define três tentativas de entrega, esperas de 1 e 2 segundos, timeout de conexão de 5 segundos e timeout por requisição de 10 segundos. O Compose configura a URL JDBC interna para `postgres:5432` quando a aplicação roda no container.

A documentação OpenAPI fica em `http://localhost:8080/v3/api-docs` e a interface Swagger em `http://localhost:8080/swagger-ui/index.html`.

As credenciais locais padrão são `webhook`/`webhook`, para desenvolvimento. Substitua-as em outros ambientes. Não use essas credenciais de exemplo em produção.

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

## Demonstração e documentos

Com a aplicação local em execução, abra um terminal para `python3 demo/receiver.py` e outro para `bash demo/demo.sh`. O primeiro evento representa vendas e tem sucesso imediato; o segundo representa agendamentos e recebe duas respostas `500` antes do sucesso. O script mostra o mesmo serviço reutilizado sem mudanças no código. Detalhes estão em [arquitetura](docs/arquitetura.md), [roteiro de demonstração](docs/demonstracao.md), [relatório técnico](docs/relatorio-tecnico.pdf) e [apresentação](docs/apresentacao.pdf).

Os arquivos Markdown são as fontes editáveis dos PDFs. Para regenerá-los, instale `reportlab` em um ambiente Python e execute `python3 docs/gerar-pdfs.py`.
