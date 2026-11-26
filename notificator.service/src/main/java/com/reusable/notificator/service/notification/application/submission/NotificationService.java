package com.reusable.notificator.service.notification.application.submission;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.reusable.notificator.service.notification.application.channel.NotificationSenderRegistry;
import com.reusable.notificator.service.notification.infrastructure.persistence.Notification;
import com.reusable.notificator.service.notification.infrastructure.persistence.NotificationRepository;
import com.reusable.notificator.service.notification.model.Channel;
import com.reusable.notificator.service.notification.model.Status;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

	private final NotificationRepository repository;
	private final ObjectProvider<NotificationPublisher> publishers;
	private final Validator validator;
	private final NotificationSenderRegistry registry;

	NotificationService(NotificationRepository repository, ObjectProvider<NotificationPublisher> publishers,
			Validator validator, NotificationSenderRegistry registry) {
		this.repository = repository;
		this.publishers = publishers;
		this.validator = validator;
		this.registry = registry;
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public Acceptance submit(SubmitNotificationRequest request) {
		validate(request);
		var sender = registry.find(request.channel())
				.orElseThrow(() -> new InvalidRequest("This channel has no registered sender."));
		if (!sender.isAvailable()) {
			throw new ChannelUnavailable(request.channel());
		}
		// The repository commits the insert before the publisher can see the ID.
		var id = repository.save(new Notification(request.source(), request.channel(), request.content())).getId();
		try {
			var publisher = publishers.getIfAvailable();
			if (publisher == null) {
				throw new NotificationPublicationException("Notification publisher is unavailable.");
			}
			publisher.publish(id);
		} catch (NotificationPublicationException failure) {
			repository.failPublication(id, "Notification could not be queued.");
			throw failure;
		}
		return new Acceptance(id, Status.PENDING);
	}

	public Optional<Notification> find(UUID id) {
		return repository.findById(id);
	}

	private void validate(SubmitNotificationRequest request) {
		var violations = validator.validate(request);
		if (!violations.isEmpty()) {
			var fields = violations.stream().map(violation -> violation.getPropertyPath().toString())
					.distinct().sorted().collect(Collectors.joining(", "));
			throw new InvalidRequest("Check these fields: " + fields);
		}
		switch (request.channel()) {
			case EMAIL -> validateEmail(request);
			case TELEGRAM -> validateChatId(request.recipient());
		}
	}

	private void validateEmail(SubmitNotificationRequest request) {
		var subject = request.subject();
		if (subject == null || subject.isBlank() || subject.contains("\r") || subject.contains("\n")) {
			throw new InvalidRequest("EMAIL requires a non-blank, single-line subject.");
		}
		try {
			var address = new InternetAddress(request.recipient(), true);
			address.validate();
			if (!request.recipient().equals(address.getAddress())) {
				throw new InvalidRequest("EMAIL requires one email address.");
			}
		} catch (AddressException failure) {
			throw new InvalidRequest("EMAIL requires one valid email address.");
		}
	}

	private void validateChatId(String recipient) {
		try {
			if (Long.parseLong(recipient) != 0) {
				return;
			}
		} catch (NumberFormatException failure) {
			// Use the same public error for all invalid chat IDs.
		}
		throw new InvalidRequest("TELEGRAM requires a nonzero numeric chat ID.");
	}

	public record Acceptance(UUID id, Status status) {
	}

	public static final class InvalidRequest extends RuntimeException {
		InvalidRequest(String message) {
			super(message);
		}
	}

	public static final class ChannelUnavailable extends RuntimeException {
		ChannelUnavailable(Channel channel) {
			super(channel + " is currently unavailable.");
		}
	}
}
