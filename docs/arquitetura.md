# Arquitetura e resiliência

## Contexto e componentes

O Webhook Service é um serviço REST independente. Aplicações produtoras enviam um evento JSON e identificam um destino previamente cadastrado. O serviço localiza a URL desse destino, faz um POST síncrono e registra o resultado. O produtor depende apenas do contrato HTTP; o serviço não contém regras específicas de vendas ou agendamentos.

```text
Produtor -> EventController -> DeliveryService -> WebhookHttpClient -> Destinatário
                              |                     |
                              +-> DeliveryRepository +-> Resilience4j Retry
                              +-> DestinationService -> DestinationRepository
                                                PostgreSQL
```

`DestinationController` e `DestinationService` gerenciam nome, URL e ativação. `EventController` recebe eventos e consulta resultados. `DeliveryService` coordena validação do destino, persistência e Retry. `WebhookHttpClient` conhece apenas HTTP. Spring Data JPA acessa o PostgreSQL. SpringDoc gera OpenAPI e Swagger UI; Bean Validation verifica entradas; Lombok reduz código repetitivo nas entidades.

## Sequência de entrega

1. `POST /api/v1/events` valida `destinationId`, `eventType` e `payload`.
2. O destino inexistente gera `404`; o inativo gera `409`, sem chamada externa ou histórico novo.
3. `DeliveryService` persiste a entrega como `PROCESSING`, `attemptCount=0`.
4. O cliente envia o JSON com `Content-Type: application/json`. O contador cresce em cada chamada efetiva.
5. A resposta final, a exceção ou a interrupção define `DELIVERED` ou `FAILED`.
6. O serviço persiste tentativas, último status/corpo HTTP disponível, erro e `completedAt`.
7. A API devolve HTTP `200` com o resultado no corpo. `GET /api/v1/deliveries/{id}` recupera o resultado persistido.

As gravações inicial e final são transações separadas do repositório. Não há transação de banco aberta durante a chamada externa nem durante as esperas do Retry.

## Política de resiliência

O padrão aplicado é **Retry**, implementado com `resilience4j-retry:2.4.0` por decoração explícita da chamada HTTP. Assim, o Retry atua sobre o envio, sem depender de interceptação AOP ou de chamadas internas entre métodos da mesma classe.

| Resultado | Decisão |
| --- | --- |
| HTTP `2xx` | `DELIVERED`, sem nova tentativa |
| HTTP `408`, `429` ou `5xx` | Repetir até o limite |
| Outros códigos HTTP, inclusive `404` | `FAILED`, sem Retry |
| `IOException`, inclusive timeout e conexão | Repetir até o limite |
| `InterruptedException` | Restaurar interrupção e finalizar `FAILED` |

`application.yaml` define três tentativas totais, esperas de 1 e 2 segundos, timeout de conexão de 5 segundos e timeout por requisição de 10 segundos. `Retry-After` ainda não é interpretado. O processamento é síncrono: o produtor aguarda o resultado final.

## Limites e evolução

O Retry fornece semântica de entrega **pelo menos uma vez**. Se o destinatário processar o evento e perder a resposta, poderá receber o mesmo payload novamente. A API ainda não fornece chave de idempotência ou deduplicação. Também não há autenticação, fila, processamento assíncrono, migrações versionadas ou histórico individual por tentativa. O esquema é atualizado automaticamente pelo Hibernate apenas nesta configuração inicial; produção deve usar migrações e credenciais próprias.
