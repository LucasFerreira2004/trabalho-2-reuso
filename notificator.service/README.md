# Notification service

This Java 21 and Spring Boot service consumes JSON messages from RabbitMQ and sends plain-text email through SMTP. A producer can publish notifications using any AMQP 0-9-1 client.

## Message contract

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

Start [RabbitMQ](https://www.rabbitmq.com/docs/download) and [Mailpit](https://mailpit.axllent.org/docs/install/docker/) with Docker, then run the service with Java 21.

```bash
docker run -d --rm --name notification-rabbitmq \
  -p 127.0.0.1:5672:5672 -p 127.0.0.1:15672:15672 \
  -e RABBITMQ_DEFAULT_USER=demo -e RABBITMQ_DEFAULT_PASS=demo \
  rabbitmq:4-management

docker run -d --rm --name notification-mailpit \
  -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 \
  axllent/mailpit

SPRING_RABBITMQ_USERNAME=demo SPRING_RABBITMQ_PASSWORD=demo ./mvnw spring-boot:run
```

1. Open the [RabbitMQ management UI](http://localhost:15672) and sign in with **demo** as both username and password.
2. Open the **notifications.email** queue and expand **Publish message**. Set **content_type** to **application/json** in the properties, paste the example into the payload, and publish.
3. Open the [Mailpit inbox](http://localhost:8025) to see the delivered email. To test rejection, publish the example with an empty **to** array and inspect the **notifications.email.failed** queue.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
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

Run the automated tests with `./mvnw test`.
