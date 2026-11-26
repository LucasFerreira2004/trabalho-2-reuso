package com.reusable.notificator.service.notification;

/**
 * A known failure to publish or route a notification message.
 */
public class NotificationPublicationException extends RuntimeException {

	public NotificationPublicationException(String message) {
		super(message);
	}

	public NotificationPublicationException(String message, Throwable cause) {
		super(message, cause);
	}
}
