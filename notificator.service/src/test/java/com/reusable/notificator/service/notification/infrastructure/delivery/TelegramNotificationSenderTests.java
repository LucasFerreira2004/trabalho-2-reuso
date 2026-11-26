package com.reusable.notificator.service.notification.infrastructure.delivery;

import java.time.Duration;

import com.reusable.notificator.service.notification.application.channel.NotificationDeliveryException;
import com.reusable.notificator.service.notification.model.Channel;
import com.reusable.notificator.service.notification.model.NotificationContent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TelegramNotificationSenderTests {

	private static final String SEND_URL = "https://api.telegram.org/bot123456:local-test/sendMessage";
	private static final NotificationContent CONTENT = new NotificationContent("-1234", null, "Hello.");

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.telegram.org");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final TelegramNotificationSender sender = new TelegramNotificationSender(builder.build(), "123456:local-test");

	@Test
	void sendsTextToTheConfiguredChat() {
		server.expect(requestTo(SEND_URL)).andExpect(method(HttpMethod.POST))
				.andExpect(content().json("{\"chat_id\":-1234,\"text\":\"Hello.\"}"))
				.andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":1}}", MediaType.APPLICATION_JSON));
		assertThat(sender.channel()).isEqualTo(Channel.TELEGRAM);
		sender.send(CONTENT);
		server.verify();
	}

	@Test
	void missingTokenKeepsTheChannelUnavailable() {
		var unconfigured = new TelegramNotificationSender(builder.build(), " ");
		assertThat(unconfigured.isAvailable()).isFalse();
		assertThatThrownBy(() -> unconfigured.send(CONTENT)).isInstanceOf(NotificationDeliveryException.class);
		server.verify();
	}

	@Test
	void permanentFailureHidesProviderDetailsAndToken() {
		server.expect(requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN)
				.body("{\"ok\":false,\"description\":\"private provider detail\"}")
				.contentType(MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> sender.send(CONTENT))
				.isInstanceOfSatisfying(NotificationDeliveryException.class, failure -> {
					assertThat(failure.isRetryable()).isFalse();
					assertThat(failure.getMessage()).isEqualTo("Telegram rejected the message.");
					assertThat(failure.getCause()).isNull();
				});
		server.verify();
	}

	@Test
	void readsTheProviderRateLimitDelay() {
		server.expect(requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
				.body("{\"ok\":false,\"error_code\":429,\"parameters\":{\"retry_after\":2}}")
				.contentType(MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> sender.send(CONTENT))
				.isInstanceOfSatisfying(NotificationDeliveryException.class, failure -> {
					assertThat(failure.isRetryable()).isTrue();
					assertThat(failure.retryAfter()).isEqualTo(Duration.ofSeconds(2));
				});
		server.verify();
	}
}
