# Fluxo de entrega e Retry

## Fluxo implementado

O `EventController` expõe `POST /api/v1/events` e `GET /api/v1/deliveries/{id}`; ambos chamam `DeliveryService` diretamente. O POST recebe `destinationId` numérico, `eventType` e `payload` JSON. O serviço consulta o destino e rejeita IDs inexistentes (`404`) ou destinos inativos (`409`) antes de criar uma entrega. O serviço grava `Delivery` com `PROCESSING` e `attemptCount = 0` no PostgreSQL. Em seguida, `WebhookHttpClient` envia o payload por HTTP POST, com `Content-Type: application/json`, e retorna a resposta sem acessar o banco.

O serviço usa Resilience4j Retry diretamente ao redor da chamada HTTP. Cada invocação efetiva do cliente incrementa o contador em memória. Ao terminar, o serviço grava uma segunda vez a entrega com status, número de tentativas, último código e corpo HTTP disponível, mensagem de erro e `completedAt`. As duas chamadas ao repositório usam transações separadas; nenhuma transação permanece aberta durante HTTP ou espera entre tentativas.

## Política e configuração

O projeto usa `resilience4j-retry:2.4.0` diretamente. A configuração está em `src/main/resources/application.yaml`:

| Propriedade | Valor local | Efeito |
| --- | --- | --- |
| `webhook.retry.max-attempts` | `3` | Inclui a chamada inicial. |
| `webhook.retry.initial-wait` | `1s` | Espera após a primeira falha temporária. |
| `webhook.retry.multiplier` | `2` | A segunda espera é de 2 segundos. |
| `webhook.http.connect-timeout` | `5s` | Limite de conexão. |
| `webhook.http.request-timeout` | `10s` | Limite de cada chamada HTTP. |

HTTP `2xx` conclui como `DELIVERED`. HTTP `408`, `429` e `5xx`, além de timeout e falha de conexão (`IOException`), acionam Retry. Outros códigos, incluindo `404`, falham sem repetição. Uma interrupção não é repetida: o serviço restaura o sinal de interrupção e encerra como `FAILED`. O cabeçalho `Retry-After` ainda não é considerado.

## Resposta e consulta

O endpoint de eventos devolve HTTP `200` quando processa a solicitação, mesmo se a entrega externa terminar em `FAILED`. O corpo contém `deliveryId`, `status`, `attemptCount`, `responseStatus` e `errorMessage`. `GET /api/v1/deliveries/{id}` recupera o resultado persistido. Os IDs atuais são números (`Long`), não UUIDs.

```json
{"deliveryId": 42, "status": "DELIVERED", "attemptCount": 3, "responseStatus": 200, "errorMessage": null}
```

## Validação e limite conhecido

Na raiz do repositório, execute `docker compose up -d --wait` e `./mvnw test`. Os testes usam PostgreSQL e um servidor HTTP local para verificar sucesso, recuperação após dois erros `500`, esgotamento das três tentativas, `404` sem Retry, respostas `408` e `429`, timeout e rejeição de destinos inativos ou ausentes. O ambiente de teste reduz apenas o tempo de espera e o timeout HTTP.

O Retry oferece entrega *pelo menos uma vez*: se o destinatário processar o evento e sua resposta se perder, ele poderá receber o mesmo payload novamente. Ainda não há chave de idempotência nem deduplicação no destinatário.
