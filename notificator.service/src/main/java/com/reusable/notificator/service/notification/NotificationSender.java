package com.reusable.notificator.service.notification;

public interface NotificationSender {

	Channel channel();

	/**
	 * Sends the content through the provider. A normal return means the provider accepted it.
	 */
	void send(NotificationContent content);
}
