package com.reusable.notificator.service.notification.infrastructure.messaging;

import java.util.Objects;
import java.util.UUID;

/**
 * Payload shared by the publisher and the listener. The ID refers to a persisted notification.
 */
public record NotificationMessage(UUID notificationId) {

	public NotificationMessage {
		Objects.requireNonNull(notificationId, "notificationId is required");
	}
}
