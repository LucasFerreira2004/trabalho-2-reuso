package com.reusable.notificator.service.notification.application.processing;

import java.util.UUID;

public interface NotificationProcessor {

	/**
	 * Processes a persisted notification and returns the outcome after committing status changes.
	 *
	 * @throws NotificationProcessingException if an infrastructure failure prevents returning a reliable outcome
	 */
	ProcessingResult process(UUID id) throws NotificationProcessingException;
}
