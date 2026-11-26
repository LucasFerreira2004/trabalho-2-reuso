package com.reusable.notificator.service.notification;

import java.time.Duration;

/**
 * A provider failure with a public message safe to include in notification status.
 */
public class NotificationDeliveryException extends RuntimeException {

	private final boolean retryable;
	private final Duration retryAfter;

	public NotificationDeliveryException(String message, boolean retryable) {
		this(message, retryable, Duration.ZERO);
	}

	public NotificationDeliveryException(String message, boolean retryable, Duration retryAfter) {
		super(message);
		this.retryable = retryable;
		this.retryAfter = retryAfter;
	}

	public boolean isRetryable() {
		return retryable;
	}

	public Duration retryAfter() {
		return retryAfter;
	}
}
