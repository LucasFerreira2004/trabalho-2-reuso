package com.reusable.notificator.service.notification;

/**
 * An infrastructure failure encountered while determining or persisting a processing outcome.
 */
public class NotificationProcessingException extends RuntimeException {

	public NotificationProcessingException(String message, Throwable cause) {
		super(message, cause);
	}
}
