package com.reusable.notificator.service.notification.application.submission;

import java.util.UUID;

public interface NotificationPublisher {

	/**
	 * Publishes the ID of a committed notification in a message.
	 * A normal return confirms acceptance and routing by the broker.
	 *
	 * @throws NotificationPublicationException if publication or routing is known to have failed
	 */
	void publish(UUID id) throws NotificationPublicationException;
}
