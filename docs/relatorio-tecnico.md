# Webhook Service

Serviço reutilizável de entrega de eventos

## 1. Introdução
Este trabalho adota a abordagem de serviço reutilizável. O artefato é uma API REST independente que recebe eventos JSON de aplicações produtoras e os encaminha por HTTP a sistemas destinatários previamente cadastrados. A funcionalidade é genérica: não contém entidades de vendas, agendamentos ou outros domínios. Cada produtor informa um destino, um tipo de evento e um payload. Dessa forma, consumidores de contextos diferentes reutilizam o mesmo cadastro, envio, tratamento de falhas e consulta de resultados.

O problema tratado é a repetição da lógica de integração. Sem um serviço comum, cada aplicação precisa implementar envio HTTP, armazenamento de destinos, controle de timeout, novas tentativas e histórico. Além do custo de implementação, políticas diferentes entre produtores dificultam manutenção e observação. A solução centraliza essas responsabilidades em um contrato HTTP documentado por OpenAPI.

O objetivo é demonstrar reuso por serviço e um padrão de resiliência, como exigido no enunciado. O padrão escolhido é Retry. O trabalho também entrega código-fonte, instruções de execução, configuração em Docker Compose, testes automatizados e uma demonstração de dois contextos consumidores. O escopo atual é síncrono: a resposta ao produtor é enviada somente após a entrega ou esgotamento das tentativas. Não há mensageria nem processamento em segundo plano.

## 2. Descrição da solução
O Webhook Service oferece operações para cadastrar, consultar, atualizar e desativar destinos. Um destino tem identificador numérico, nome, URL HTTP/HTTPS, estado ativo e datas de criação e atualização. A desativação impede novos envios sem excluir os registros anteriores. O produtor chama `POST /api/v1/events` com `destinationId`, `eventType` e `payload`. O serviço verifica que o destino existe e está ativo antes de persistir a entrega inicial.

A entrega possui destino associado, tipo, payload, status, contador de tentativas, último status e corpo HTTP, erro e horários. O registro é criado como `PROCESSING` com zero tentativas. Depois do envio, é atualizado para `DELIVERED` quando o destinatário responde com código 2xx, ou `FAILED` em falhas definitivas e após esgotar o Retry. A rota `GET /api/v1/deliveries/{id}` permite consultar o resultado persistido.

A API separa o processamento da solicitação do resultado externo. Assim, `POST /api/v1/events` responde com HTTP 200 quando a solicitação foi processada, mesmo se o destinatário terminou em `FAILED`; o corpo informa `deliveryId`, `status`, `attemptCount`, `responseStatus` e `errorMessage`. Dados inválidos retornam 400, destino ou entrega ausente retorna 404 e destino inativo retorna 409. Essa convenção deve ser considerada pelo produtor: ele precisa inspecionar `status`, não somente o código HTTP da API.

## 3. Arquitetura e tecnologias
A implementação usa Java 25 e Spring Boot 4.1.2. Spring Web MVC expõe os endpoints; Bean Validation verifica os campos de entrada; Spring Data JPA e Hibernate persistem entidades no PostgreSQL 17; SpringDoc 3.2.0 publica OpenAPI e Swagger UI; Resilience4j Retry 2.4.0 controla repetição de chamadas HTTP. Maven Wrapper padroniza o build. Docker Compose disponibiliza o banco e, no perfil `full`, a aplicação inteira.

A arquitetura separa responsabilidades. `DestinationController` recebe pedidos de cadastro e manutenção; `DestinationService` executa as regras; `DestinationRepository` acessa o banco. `EventController` recebe eventos e consulta entregas. `DeliveryService` valida o destino, cria o registro, coordena tentativas e grava o resultado. `WebhookHttpClient` constrói e envia a requisição POST por meio do cliente HTTP do Java, sem acessar o banco ou alterar entidades. `GlobalExceptionHandler` transforma falhas de validação e domínio em respostas HTTP estruturadas.

Essa separação protege o serviço contra acoplamento indevido entre comunicação e persistência. O Retry envolve apenas o envio externo. A gravação inicial e a final são duas operações transacionais independentes; o serviço não mantém uma transação aberta durante a espera entre tentativas. O esquema local usa `ddl-auto: update` para facilitar a demonstração, mas uma implantação real precisa de migrações versionadas. O banco e a aplicação usam configurações distintas de URL JDBC dentro e fora dos containers.

## 4. Padrões de reuso e resiliência
O principal mecanismo de reuso é o contrato de serviço. Aplicações produtoras se integram por HTTP e JSON, sem importar classes Java nem depender do domínio interno do Webhook Service. O destino é configurável e o payload permanece genérico; isso permite novos produtores e destinatários sem alterar o núcleo. A API versionada sob `/api/v1` e a documentação OpenAPI tornam a integração explícita. A separação por camadas e a injeção de dependências também favorecem testes e substituição de componentes.

O padrão de resiliência é Retry com até três tentativas, incluindo a inicial. A primeira repetição espera um segundo; a segunda, dois segundos. Respostas HTTP 408, 429 e 5xx são tratadas como potencialmente temporárias. Timeout e falha de conexão, representados por `IOException`, também são repetidos. HTTP 2xx conclui a entrega; outros códigos, como 404, falham sem repetição. Interrupção da thread encerra o processamento e preserva o sinal de interrupção.

O mecanismo é aplicado programaticamente por `Retry.decorateCallable`, diretamente sobre `WebhookHttpClient.send`. Isso evita depender de interceptação por anotação em um método chamado dentro da própria classe. O contador é incrementado em cada chamada efetiva. O resultado persistido usa a última resposta HTTP disponível. O cabeçalho `Retry-After` de uma resposta 429 ainda não altera a espera. O aumento de confiabilidade tem um limite: o destinatário pode processar o evento e perder a resposta, levando a um novo envio do mesmo payload.

## 5. Demonstração de reutilização
A demonstração usa dois produtores conceituais: vendas e agendamentos. Ambos chamam a mesma API, mas enviam tipos e payloads diferentes. O evento de vendas é `pedido_criado` com `orderId`. O evento de agendamento é `consulta_marcada` com `appointmentId`. Nenhum desses campos aparece como regra especial no código do serviço; ambos são apenas JSON encaminhado ao destino cadastrado.

O repositório inclui `demo/receiver.py`, um destinatário HTTP local, e `demo/demo.sh`, que cadastra dois destinos e envia os dois eventos. O destinatário responde 200 à primeira chamada, 500 às duas chamadas seguintes e 200 à quarta. Assim, a primeira entrega termina como `DELIVERED` após uma tentativa, e a segunda como `DELIVERED` após três tentativas. O terminal do destinatário imprime cada payload recebido, enquanto o script mostra as respostas da API. A demonstração evidencia tanto reuso em contextos distintos quanto recuperação de falha temporária.

Para reproduzir, inicia-se o PostgreSQL com `docker compose up -d --wait`, a aplicação com `./mvnw spring-boot:run`, o destinatário com `python3 demo/receiver.py` e, em outro terminal, o script com `bash demo/demo.sh`. Também é possível iniciar aplicação e banco via perfil `full` do Compose; para a demonstração local, o processo Maven simplifica o acesso ao destinatário em `127.0.0.1:8090`.

## 6. Verificação e resultados
Os testes JUnit 5 usam PostgreSQL real e um servidor HTTP local criado durante a execução. Há testes de contexto, persistência JPA, endpoints e Retry. O fluxo de API verifica cadastro, consulta, atualização, desativação, entrega bem-sucedida e consulta posterior do resultado. Os testes de erro verificam entrada inválida, URL malformada, payload ausente, JSON inválido, destino inexistente e status HTTP apropriado.

Os cenários de Retry cobrem sucesso após duas respostas 500, esgotamento em três respostas 500, falha 404 sem repetição, respostas 408 e 429 seguidas de sucesso, timeout, falha de conexão e rejeição de destino inativo ou ausente. Os testes inspecionam o registro persistido, incluindo estado inicial `PROCESSING`, contador final, status HTTP, corpo, erro e horário de conclusão. Para manter a suíte rápida, os testes reduzem as esperas e o timeout via propriedades de teste, sem mudar a política de produção.

No ambiente de desenvolvimento, `./mvnw test` valida o build e os cenários automatizados; `docker compose --profile full up -d --build --wait` valida a execução conjunta. Os resultados devem ser reproduzidos antes da apresentação, especialmente após alterações de dependências ou configuração. Os testes demonstram os casos definidos, mas não substituem testes de carga, segurança, falha do banco durante a entrega ou redes com latência variável.

## 7. Conclusão e limites
O serviço desenvolvido permite reutilizar o mesmo mecanismo de entrega de eventos em contextos diferentes. A API documentada, o destino configurável e o payload genérico reduzem a necessidade de duplicar lógica de HTTP, cadastro e Retry em cada aplicação produtora. A separação entre serviço de entrega, cliente HTTP e repositórios facilita evolução e testes. O padrão Retry demonstrou recuperação de falhas temporárias sem repetir erros definitivos como HTTP 404.

As principais limitações são a execução síncrona, ausência de autenticação, inexistência de fila e falta de deduplicação. A entrega tem semântica de pelo menos uma vez: um destinatário pode receber o mesmo evento mais de uma vez. O identificador `deliveryId` poderia ser enviado em um cabeçalho para apoiar idempotência no destinatário. Em uma evolução de produção, também seriam necessários segredo ou API Key, migrações de banco, gestão externa de credenciais, monitoramento e políticas de timeout e Retry ajustadas com dados de operação.

O trabalho mostra que reuso por serviço depende tanto do contrato público quanto de limites claros entre componentes internos. A configuração externa permite ajustar comportamento sem recompilar, enquanto os testes verificam que o núcleo preserva a política definida. A demonstração de vendas e agendamentos apresenta a mesma solução em dois cenários sem regras de negócio embutidas no serviço.

## Referências do projeto
Código-fonte, `README.md`, especificação OpenAPI em `/v3/api-docs`, documentação de arquitetura e enunciado “Trabalho 2 – Desenvolvimento de Serviço Reutilizável”, QXD0068.
