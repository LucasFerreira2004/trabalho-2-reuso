package com.reusable.notificator.service.notification;

import java.util.UUID;

public interface NotificationPublisher {

	/**
	 * Publishes the ID of a committed notification as a {@link NotificationMessage}.
	 * A normal return confirms acceptance and routing by the broker.
	 *
	 * @throws NotificationPublicationException if publication or routing is known to have failed
	 */
	void publish(UUID id) throws NotificationPublicationException;
}
