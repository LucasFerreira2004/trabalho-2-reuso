package com.reusable.notificator.service.notification.application.submission;

import com.reusable.notificator.service.notification.model.Channel;
import com.reusable.notificator.service.notification.model.NotificationContent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitNotificationRequest(
		@NotBlank @Size(max = 120) String source,
		@NotNull Channel channel,
		@NotBlank @Size(max = 320) String recipient,
		@Size(max = 255) String subject,
		@NotBlank @Size(max = 4096) String message) {

	NotificationContent content() {
		return new NotificationContent(recipient, subject, message);
	}
}
