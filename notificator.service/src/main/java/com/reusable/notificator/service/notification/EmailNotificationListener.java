package com.reusable.notificator.service.notification;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class EmailNotificationListener {

	private final EmailNotificationSender notificationSender;

	EmailNotificationListener(EmailNotificationSender notificationSender) {
		this.notificationSender = notificationSender;
	}

	@RabbitListener(id = "emailNotificationListener", queues = "${notification.queue}")
	public void receive(EmailNotificationRequest request) {
		notificationSender.send(request);
	}
}
