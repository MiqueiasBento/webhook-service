# Webhook Service — reuso por serviço
- Uma API de entrega de eventos para diferentes aplicações
- Demonstração: vendas e agendamentos
Nota: Problema de duplicar a lógica de webhooks em cada sistema.

# Problema e objetivo
- Cada produtor repetiria cadastro de URL, HTTP, timeout e Retry
- Centralizar essa funcionalidade em um serviço independente
- Reuso por contrato REST e JSON, sem código específico de negócio
Nota: O payload e o tipo do evento são genéricos.

# Arquitetura
- Produtor → API → DeliveryService → WebhookHttpClient → Destinatário
- DestinationService e repositórios persistem configuração e histórico
- PostgreSQL, Spring Boot 4, Java 25, SpringDoc e Resilience4j
Nota: Separação entre comunicação HTTP e persistência.

# Contrato HTTP
- POST /api/v1/destinations cadastra um destinatário
- POST /api/v1/events recebe destinationId, eventType e payload
- GET /api/v1/deliveries/{id} consulta o resultado
- Swagger UI: /swagger-ui/index.html
Nota: Abrir o Swagger e mostrar o formato do evento.

# Persistência e resiliência
- Entrega inicial: PROCESSING, zero tentativas
- Retry apenas em 408, 429, 5xx e falhas de conexão ou timeout
- Máximo de três chamadas; intervalos de 1 e 2 segundos
- Resultado final: DELIVERED ou FAILED, com contagem e última resposta
Nota: Explicar por que HTTP 404 não deve ser repetido.

# Demonstração: dois produtores
- Vendas envia pedido_criado: sucesso na primeira chamada
- Agendamentos envia consulta_marcada: 500, 500, 200
- Mesmo serviço, destinos configuráveis e payloads diferentes
Nota: Executar python3 demo/receiver.py e bash demo/demo.sh em terminais separados.

# Verificação e limites
- Testes JUnit exercitam API, banco real, timeout e políticas de Retry
- Entrega pelo menos uma vez: duplicatas são possíveis
- Ainda faltam autenticação, deduplicação, fila e migrações de produção

# Conclusão
- Reuso reduz código duplicado nos produtores
- Contrato HTTP documentado facilita integração
- Retry melhora disponibilidade diante de falhas temporárias
- Próxima evolução: idempotência, segurança e operação assíncrona
Nota: Diferença entre HTTP 200 da API e status da entrega no corpo.
