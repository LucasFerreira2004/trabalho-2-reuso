package com.reusable.notificator.service.notification.infrastructure.messaging;

import com.reusable.notificator.service.notification.infrastructure.delivery.EmailNotificationRequest;
import com.reusable.notificator.service.notification.infrastructure.delivery.EmailNotificationSender;
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
