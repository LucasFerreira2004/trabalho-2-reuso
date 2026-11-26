package com.reusable.notificator.service.notification.infrastructure.resilience;

import java.time.Duration;

import com.reusable.notificator.service.notification.application.channel.NotificationDeliveryException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class NotificationRetryConfiguration {

	private static final long DEFAULT_DELAY_MILLIS = 250;
	private static final Duration MAX_PROVIDER_DELAY = Duration.ofSeconds(2);

	@Bean
	public Retry notificationRetry() {
		var config = RetryConfig.custom()
				.maxAttempts(3)
				.retryOnException(failure -> failure instanceof NotificationDeliveryException delivery
						&& delivery.isRetryable() && delivery.retryAfter().compareTo(MAX_PROVIDER_DELAY) <= 0)
				.intervalBiFunction((attempt, outcome) -> {
					if (outcome.isLeft() && outcome.getLeft() instanceof NotificationDeliveryException delivery) {
						return Math.max(DEFAULT_DELAY_MILLIS, delivery.retryAfter().toMillis());
					}
					return DEFAULT_DELAY_MILLIS;
				})
				.build();
		return Retry.of("notification-delivery", config);
	}
}
