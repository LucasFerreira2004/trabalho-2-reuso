package com.reusable.notificator.service.notification.application.channel;

import com.reusable.notificator.service.notification.model.Channel;
import com.reusable.notificator.service.notification.model.NotificationContent;

public interface NotificationSender {

	Channel channel();

	default boolean isAvailable() {
		return true;
	}

	/**
	 * Sends the content through the provider. A normal return means the provider accepted it.
	 */
	void send(NotificationContent content);
}
