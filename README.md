# Serviço de Notificações

Serviço reutilizável para enviar notificações por e-mail e Telegram. Os sistemas clientes solicitam um envio pela API REST e recebem um identificador para acompanhar o status e as tentativas. O processamento é assíncrono e o histórico fica no PostgreSQL.

## Estrutura do projeto

O código da aplicação fica em `notificator.service/`. Os pacotes abaixo fazem parte do módulo `notification`.

| Pacote | Responsabilidade |
| --- | --- |
| `api` | Endpoints HTTP de solicitação e consulta |
| `application` | Validação e aceitação da solicitação, processamento e seleção do canal |
| `model` | Tipos compartilhados para canal, status e conteúdo |
| `infrastructure` | Persistência, mensageria, provedores de envio e política de Retry |

O enunciado e as decisões do projeto ficam em `docs/`. [Escopo e plano de desenvolvimento](docs/Notify-Service-Escopo-Reduzido.md)

## Como executar localmente

Você precisa de Java 21 e Docker com Docker Compose. O Maven Wrapper já vem no repositório.

Na raiz, crie o arquivo `.env` a partir do modelo, defina `POSTGRES_PASSWORD` e inicie o PostgreSQL.

```bash
test -f .env || cp .env.example .env
docker compose up -d --wait postgres
```

Inicie o Mailpit para receber os e-mails da demonstração.

```bash
docker run -d --rm --name notification-mailpit \
  -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 \
  axllent/mailpit
```

Carregue as variáveis e execute a aplicação com o perfil `core-demo`, que usa uma fila local em memória para processar os envios da API.

```bash
set -a
. ./.env
set +a
cd notificator.service
SMTP_HOST=127.0.0.1 SMTP_PORT=1025 SMTP_USERNAME= SMTP_PASSWORD= \
  SMTP_AUTH=false SMTP_STARTTLS=false NOTIFICATION_FROM=notifications@example.com \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=core-demo
```

## Enviar e consultar uma notificação

Em outro terminal, solicite o envio de um e-mail.

```bash
curl -i http://127.0.0.1:8080/notifications \
  -H 'Content-Type: application/json' \
  -d '{"source":"biblioteca-demo","channel":"EMAIL","recipient":"leitor@example.com","subject":"Lembrete de devolução","message":"Seu empréstimo vence hoje."}'
```

A API retorna HTTP 202 e um `id`. Use esse valor para consultar o status até chegar a `SENT`.

```bash
ID='cole-o-id-aqui'
curl -i "http://127.0.0.1:8080/notifications/$ID"
```

Confira o e-mail recebido ou explore os endpoints interativamente. [Caixa de entrada do Mailpit](http://127.0.0.1:8025) · [Swagger UI](http://127.0.0.1:8080/swagger-ui/index.html)

O guia técnico reúne os detalhes de configuração, os cenários de teste do Telegram e o fluxo de e-mail via RabbitMQ. [Guia técnico do módulo](notificator.service/README.md)
