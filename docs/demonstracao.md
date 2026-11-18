# Demonstração e apresentação

## Preparação

Use quatro terminais na raiz de `webhook-service/`. Antes da apresentação, confirme que as portas `5433`, `8080` e `8090` estão livres. A sequência abaixo usa a aplicação local, pois o destinatário de exemplo escuta em `127.0.0.1:8090`.

1. Banco: `docker compose up -d --wait`.
2. Defina `export API_KEY='substitua-por-uma-chave-secreta'` e inicie a aplicação com `./mvnw spring-boot:run`.
3. Destinatário: `python3 demo/receiver.py`.
4. Verificação: `./mvnw test` e, se desejar, abra `http://localhost:8080/swagger-ui/index.html`.

Execute `bash demo/demo.sh` no quarto terminal. O script cadastra dois destinos e envia dois eventos de domínios diferentes. O resultado esperado é `pedido_criado` com `DELIVERED`, `attemptCount: 1`; depois `consulta_marcada` com `DELIVERED`, `attemptCount: 3`. O destinatário imprime a sequência de respostas `200`, `500`, `500`, `200` e os payloads recebidos. A segunda entrega leva cerca de três segundos pelas esperas de Retry.

Se repetir a demonstração, reinicie `demo/receiver.py` para zerar seu contador. Os destinos antigos permanecem no banco, mas o script cadastra novos IDs. Para consultar um resultado, copie o `deliveryId` devolvido pelo script e execute `curl -H "X-API-Key: $API_KEY" http://localhost:8080/api/v1/deliveries/ID`.

## Apresentação

1. Problema e objetivo de reuso.
2. Arquitetura e contrato OpenAPI.
3. Persistência e Retry.
4. Demonstração dos dois produtores.
5. Testes, limites e conclusão.
