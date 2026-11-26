package com.reusable.notificator.service.notification;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PersistedNotificationProcessor implements NotificationProcessor {

	private final NotificationRepository repository;
	private final NotificationSenderRegistry registry;

	PersistedNotificationProcessor(NotificationRepository repository, NotificationSenderRegistry registry) {
		this.repository = repository;
		this.registry = registry;
	}

	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ProcessingResult process(UUID id) {
		try {
			if (repository.claim(id) == 0) {
				return repository.findById(id).map(this::priorResult).orElse(ProcessingResult.NOT_FOUND);
			}
			var notification = repository.findById(id).orElseThrow();
			var sender = registry.find(notification.getChannel());
			if (sender.isEmpty()) {
				return fail(id, "No sender is available for this channel.");
			}
			try {
				deliver(id, sender.get(), notification.getContent());
			} catch (NotificationDeliveryException failure) {
				return fail(id, failure.getMessage());
			}
			requireUpdated(repository.finish(id, Status.SENT, null, Instant.now()));
			return ProcessingResult.SENT;
		} catch (RuntimeException failure) {
			throw new NotificationProcessingException("Could not process notification " + id + ".", failure);
		}
	}

	private ProcessingResult priorResult(Notification notification) {
		return switch (notification.getStatus()) {
			case PROCESSING -> ProcessingResult.ALREADY_PROCESSING;
			case SENT, FAILED -> ProcessingResult.ALREADY_FINALIZED;
			case PENDING -> throw new IllegalStateException("Processing claim did not persist.");
		};
	}

	private void deliver(UUID id, NotificationSender sender, NotificationContent content) {
		requireUpdated(repository.recordAttempt(id));
		sender.send(content);
	}

	private ProcessingResult fail(UUID id, String errorMessage) {
		requireUpdated(repository.finish(id, Status.FAILED, errorMessage, null));
		return ProcessingResult.FAILED;
	}

	private void requireUpdated(int updated) {
		if (updated != 1) {
			throw new IllegalStateException("Processing claim was lost.");
		}
	}
}
