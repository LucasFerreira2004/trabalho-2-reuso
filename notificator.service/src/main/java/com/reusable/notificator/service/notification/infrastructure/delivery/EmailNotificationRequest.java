package com.reusable.notificator.service.notification.infrastructure.delivery;

import java.util.List;

public record EmailNotificationRequest(
		List<String> to,
		String subject,
		String body,
		List<String> cc,
		List<String> bcc) {
}
