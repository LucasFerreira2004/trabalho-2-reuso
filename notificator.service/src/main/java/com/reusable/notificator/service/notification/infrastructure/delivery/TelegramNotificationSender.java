package com.reusable.notificator.service.notification.infrastructure.delivery;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import com.reusable.notificator.service.notification.application.channel.NotificationDeliveryException;
import com.reusable.notificator.service.notification.application.channel.NotificationSender;
import com.reusable.notificator.service.notification.model.Channel;
import com.reusable.notificator.service.notification.model.NotificationContent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
class TelegramNotificationSender implements NotificationSender {

	private final RestClient client;
	private final String token;

	@Autowired
	TelegramNotificationSender(@Value("${notification.telegram.bot-token}") String token,
			@Value("${notification.telegram.api-url}") String apiUrl,
			@Value("${notification.telegram.timeout-ms}") int timeoutMillis) {
		this(createClient(apiUrl, Duration.ofMillis(timeoutMillis)), token);
	}

	TelegramNotificationSender(RestClient client, String token) {
		this.client = client;
		this.token = token.strip();
	}

	@Override
	public Channel channel() {
		return Channel.TELEGRAM;
	}

	@Override
	public boolean isAvailable() {
		return !token.isEmpty();
	}

	@Override
	public void send(NotificationContent content) {
		if (!isAvailable()) {
			throw new NotificationDeliveryException("Telegram bot is unavailable.", false);
		}
		try {
			var response = client.post()
					.uri(builder -> builder.pathSegment("bot" + token, "sendMessage").build())
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("chat_id", Long.parseLong(content.recipient()), "text", content.message()))
					.retrieve().body(TelegramResponse.class);
			if (response == null) {
				throw unexpectedResponse();
			}
			if (!response.ok()) {
				var code = response.errorCode();
				throw providerFailure(code == null ? 0 : code, response.parameters());
			}
		} catch (RestClientResponseException failure) {
			var status = failure.getStatusCode().value();
			throw providerFailure(status, status == 429 ? rateLimitParameters(failure) : null);
		} catch (ResourceAccessException failure) {
			throw new NotificationDeliveryException("Telegram is temporarily unavailable.", true);
		} catch (RestClientException failure) {
			throw unexpectedResponse();
		} catch (NumberFormatException failure) {
			throw new NotificationDeliveryException("Telegram requires a valid chat ID.", false);
		}
	}

	private TelegramParameters rateLimitParameters(RestClientResponseException failure) {
		try {
			var response = failure.getResponseBodyAs(TelegramResponse.class);
			return response == null ? null : response.parameters();
		} catch (RestClientException ignored) {
			return null;
		}
	}

	private NotificationDeliveryException providerFailure(int code, TelegramParameters parameters) {
		if (code == 429) {
			var retryAfter = parameters == null ? null : parameters.retryAfter();
			long seconds = retryAfter == null ? 0 : Math.max(0, retryAfter);
			return new NotificationDeliveryException("Telegram rate limit reached.", true, Duration.ofSeconds(seconds));
		}
		if (code == 408 || (code >= 500 && code < 600)) {
			return new NotificationDeliveryException("Telegram is temporarily unavailable.", true);
		}
		return new NotificationDeliveryException("Telegram rejected the message.", false);
	}

	private NotificationDeliveryException unexpectedResponse() {
		return new NotificationDeliveryException("Telegram returned an unreadable response.", false);
	}

	private static RestClient createClient(String baseUrl, Duration timeout) {
		var http = HttpClient.newBuilder().connectTimeout(timeout).build();
		var factory = new JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(timeout);
		return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record TelegramResponse(boolean ok, @JsonProperty("error_code") Integer errorCode,
			TelegramParameters parameters) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record TelegramParameters(@JsonProperty("retry_after") Integer retryAfter) {
	}
}
