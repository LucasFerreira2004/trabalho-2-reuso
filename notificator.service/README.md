# Notification service

This Java 21 and Spring Boot service accepts notifications over HTTP, tracks them in PostgreSQL, and delivers them through SMTP or Telegram. The `core-demo` profile runs the HTTP workflow with a local, in-memory publisher. The existing direct RabbitMQ email flow is available separately while the UUID-based messaging integration is in progress.

## Existing RabbitMQ message contract

Publish a UTF-8 message with the AMQP content type set to application/json. Use the default exchange (an empty string) and the notification queue name as the routing key. The default queue is notifications.email. Mark the message as persistent if it must survive a broker restart.

```json
{
  "to": ["student@example.com"],
  "subject": "Assignment reminder",
  "body": "Your assignment is due on Friday.",
  "cc": ["teacher@example.com"],
  "bcc": ["archive@example.com"]
}
```

- **to** is a required array containing at least one email address.
- **subject** is a required non-blank line of text.
- **body** is required non-blank plain text and can span multiple lines.
- **cc** and **bcc** are optional arrays of email addresses.

The service declares the durable input queue and a durable dead-letter queue with the same name plus the **.failed** suffix. It acknowledges a delivery after SMTP accepts the email. Invalid messages and failed sends go to the dead-letter queue for inspection and manual replay. A delivery can be repeated if the service loses its broker connection after SMTP accepts the email and before RabbitMQ receives the acknowledgement.

## Run locally

### Start PostgreSQL

From the repository root, create or edit `.env`, start PostgreSQL, and export the same settings for Spring Boot.

```bash
test -f .env || cp .env.example .env
docker compose up -d --wait postgres
set -a
. ./.env
set +a
cd notificator.service
```

Flyway applies the SQL migrations in `src/main/resources/db/migration` at startup. Hibernate validates the schema that Flyway manages.

### Verify the core API and email

Start Mailpit in the terminal where you loaded the database configuration, then run the app in the `core-demo` profile.

```bash
docker run -d --rm --name notification-mailpit \
  -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 \
  axllent/mailpit

./mvnw spring-boot:run -Dspring-boot.run.profiles=core-demo
```

The local publisher accepts UUIDs into an in-process queue and invokes the real processor. The queue lives for the lifetime of the application. The RabbitMQ listener is disabled in this profile.

In another terminal, submit an email notification.

```bash
curl -i http://127.0.0.1:8080/notifications \
  -H 'Content-Type: application/json' \
  -d '{"source":"library-demo","channel":"EMAIL","recipient":"reader@example.com","subject":"Reminder","message":"Your book is due."}'
```

Expect HTTP 202 and copy the `id` from the response. Query the ID, repeating the GET until the status reaches `SENT`.

```bash
ID='PASTE_ID_HERE'
curl -i "http://127.0.0.1:8080/notifications/$ID"
```

Expect one attempt and the message in [Mailpit](http://127.0.0.1:8025). A blank message returns 400. A valid UUID with no matching notification returns 404. Both endpoints are available in [Swagger UI](http://127.0.0.1:8080/swagger-ui/index.html).

### Verify Telegram and retries without a bot

With an empty `TELEGRAM_BOT_TOKEN`, a Telegram submission returns 503 and email remains available. To exercise delivery and retries locally, start a simulated Telegram endpoint in a separate terminal.

```bash
python3 - <<'PY'
from collections import Counter
from http.server import BaseHTTPRequestHandler, HTTPServer
import json

attempts = Counter()

class TelegramStub(BaseHTTPRequestHandler):
    def do_POST(self):
        payload = json.loads(self.read_body())
        message = payload["text"]
        attempts[message] += 1
        if message.startswith("retry-") and attempts[message] < 3:
            status = 502
        elif message == "reject":
            status = 403
        else:
            status = 200
        body = json.dumps({"ok": status == 200}).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def read_body(self):
        if self.headers.get("Transfer-Encoding", "").lower() != "chunked":
            return self.rfile.read(int(self.headers["Content-Length"]))
        chunks = []
        while True:
            size = int(self.rfile.readline().split(b";", 1)[0], 16)
            if size == 0:
                self.rfile.readline()
                return b"".join(chunks)
            chunks.append(self.rfile.read(size))
            self.rfile.read(2)

HTTPServer(("127.0.0.1", 8091), TelegramStub).serve_forever()
PY
```

Restart the application from its terminal with a dummy token and the stub URL.

```bash
TELEGRAM_BOT_TOKEN=123456:local-test TELEGRAM_API_URL=http://127.0.0.1:8091 \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=core-demo
```

Submit a Telegram notification and look up its returned ID with the same GET command.

```bash
curl -i http://127.0.0.1:8080/notifications \
  -H 'Content-Type: application/json' \
  -d "{\"source\":\"core-demo\",\"channel\":\"TELEGRAM\",\"recipient\":\"-1234\",\"message\":\"retry-$(date +%s)\"}"
```

The `retry-` message reaches `SENT` after three attempts. Use `reject` as the message to observe `FAILED` after one attempt. Temporary SMTP or Telegram failures receive at most three calls in total. A Telegram rate limit delay up to two seconds is respected. A longer delay ends the current delivery attempt.

For real Telegram delivery, put the bot token in your untracked `.env`, load it in the app terminal, and restart against the default Telegram URL. Use the numeric ID of a chat the bot can reach. Telegram sends the message body. The subject applies to email.

### Try the existing RabbitMQ flow

With PostgreSQL and Mailpit running, stop the app started with `core-demo`. Start [RabbitMQ](https://www.rabbitmq.com/docs/download) with Docker, then run the app from the service module.

```bash
docker run -d --rm --name notification-rabbitmq \
  -p 127.0.0.1:5672:5672 -p 127.0.0.1:15672:15672 \
  -e RABBITMQ_DEFAULT_USER=demo -e RABBITMQ_DEFAULT_PASS=demo \
  rabbitmq:4-management

SPRING_RABBITMQ_USERNAME=demo SPRING_RABBITMQ_PASSWORD=demo ./mvnw spring-boot:run
```

1. Open the [RabbitMQ management UI](http://localhost:15672) and sign in with **demo** as both username and password.
2. Open the **notifications.email** queue and expand **Publish message**. Set **content_type** to **application/json** in the properties, paste the example into the payload, and publish.
3. Open the [Mailpit inbox](http://localhost:8025) to see the delivered email. To test rejection, publish the example with an empty **to** array and inspect the **notifications.email.failed** queue.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| POSTGRES_HOST | 127.0.0.1 | PostgreSQL host for the application |
| POSTGRES_PORT | 5432 | PostgreSQL port on the host |
| POSTGRES_DB | notifications | Database name |
| POSTGRES_USER | notifications | Database user |
| POSTGRES_PASSWORD | Set in `.env` | Database password |
| SPRING_RABBITMQ_HOST | localhost | RabbitMQ host |
| SPRING_RABBITMQ_PORT | 5672 | RabbitMQ AMQP port |
| SPRING_RABBITMQ_USERNAME | guest | RabbitMQ username |
| SPRING_RABBITMQ_PASSWORD | guest | RabbitMQ password |
| SPRING_RABBITMQ_VIRTUALHOST | / | RabbitMQ virtual host |
| NOTIFICATION_QUEUE | notifications.email | Input queue |
| NOTIFICATION_FROM | notifications@example.com | Sender address |
| SMTP_HOST | localhost | SMTP host |
| SMTP_PORT | 1025 | SMTP port |
| SMTP_USERNAME | Empty | SMTP username |
| SMTP_PASSWORD | Empty | SMTP password |
| SMTP_AUTH | false | Enable SMTP authentication |
| SMTP_STARTTLS | false | Require STARTTLS |
| TELEGRAM_BOT_TOKEN | Empty | Bot token for Telegram delivery |
| TELEGRAM_API_URL | https://api.telegram.org | Telegram API base URL |
| TELEGRAM_TIMEOUT_MS | 5000 | Telegram connect and request timeout in milliseconds |

## Tests

Run `./mvnw test` from the service module for the tests using simulated providers. With PostgreSQL running and the environment exported, run `RUN_POSTGRES_TESTS=true ./mvnw clean verify` to include repository concurrency, commit-before-publish, REST and Swagger checks.
