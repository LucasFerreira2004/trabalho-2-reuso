package com.reusable.notificator.service.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationProcessorTests {

	private final UUID id = UUID.randomUUID();
	private final NotificationContent content = new NotificationContent("reader@example.com", "Reminder", "Test.");
	private final Notification notification = new Notification("test", Channel.EMAIL, content);
	private final NotificationRepository repository = mock(NotificationRepository.class);
	private final NotificationSender sender = mock(NotificationSender.class);
	private NotificationProcessor processor;

	@BeforeEach
	void setUp() {
		when(sender.channel()).thenReturn(Channel.EMAIL);
		when(sender.isAvailable()).thenReturn(true);
		processor = new PersistedNotificationProcessor(repository, new NotificationSenderRegistry(List.of(sender)),
				new NotificationRetryConfiguration().notificationRetry());
	}

	@Test
	void sendsAndPersistsTheSuccessfulOutcome() {
		prepareClaim();
		when(repository.finish(eq(id), eq(Status.SENT), isNull(), any(Instant.class))).thenReturn(1);

		assertThat(processor.process(id)).isEqualTo(ProcessingResult.SENT);
		verify(repository).recordAttempt(id);
		verify(sender).send(content);
	}

	@Test
	void recordsProviderFailureSeparatelyFromInfrastructureErrors() {
		prepareClaim();
		doThrow(new NotificationDeliveryException("SMTP rejected the email.", false)).when(sender).send(content);
		when(repository.finish(id, Status.FAILED, "SMTP rejected the email.", null)).thenReturn(1);

		assertThat(processor.process(id)).isEqualTo(ProcessingResult.FAILED);
		verify(repository).recordAttempt(id);
		verify(sender).send(content);
	}

	@Test
	void persistsEachAttemptAndSucceedsOnTheThirdCall() {
		prepareClaim();
		doThrow(new NotificationDeliveryException("Temporary error.", true))
				.doThrow(new NotificationDeliveryException("Temporary error.", true))
				.doNothing().when(sender).send(content);
		when(repository.finish(eq(id), eq(Status.SENT), isNull(), any(Instant.class))).thenReturn(1);

		assertThat(processor.process(id)).isEqualTo(ProcessingResult.SENT);
		verify(repository, times(3)).recordAttempt(id);
		verify(sender, times(3)).send(content);
	}

	@Test
	void stopsAfterThreeTransientFailures() {
		prepareClaim();
		doThrow(new NotificationDeliveryException("Temporary error.", true)).when(sender).send(content);
		when(repository.finish(id, Status.FAILED, "Temporary error.", null)).thenReturn(1);

		assertThat(processor.process(id)).isEqualTo(ProcessingResult.FAILED);
		verify(repository, times(3)).recordAttempt(id);
		verify(sender, times(3)).send(content);
	}

	@Test
	void skipsRetryWhenTheProviderDelayExceedsTheBudget() {
		prepareClaim();
		doThrow(new NotificationDeliveryException("Rate limit reached.", true, Duration.ofSeconds(5)))
				.when(sender).send(content);
		when(repository.finish(id, Status.FAILED, "Rate limit reached.", null)).thenReturn(1);

		assertThat(processor.process(id)).isEqualTo(ProcessingResult.FAILED);
		verify(repository).recordAttempt(id);
		verify(sender).send(content);
	}

	@Test
	void skipsFinalizedAndProcessingDeliveries() {
		var alreadyProcessed = mock(Notification.class);
		when(alreadyProcessed.getStatus()).thenReturn(Status.SENT, Status.PROCESSING);
		when(repository.findById(id)).thenReturn(Optional.of(alreadyProcessed));

		assertThat(processor.process(id)).isEqualTo(ProcessingResult.ALREADY_FINALIZED);
		assertThat(processor.process(id)).isEqualTo(ProcessingResult.ALREADY_PROCESSING);
		verify(sender, never()).send(any());
		verify(repository, never()).recordAttempt(any());
	}

	@Test
	void distinguishesMissingIdsAndInfrastructureFailures() {
		assertThat(processor.process(id)).isEqualTo(ProcessingResult.NOT_FOUND);
		when(repository.claim(id)).thenThrow(new IllegalStateException("Database unavailable"));

		assertThatThrownBy(() -> processor.process(id)).isInstanceOf(NotificationProcessingException.class);
		verify(sender, never()).send(any());
	}

	private void prepareClaim() {
		when(repository.claim(id)).thenReturn(1);
		when(repository.findById(id)).thenReturn(Optional.of(notification));
		when(repository.recordAttempt(id)).thenReturn(1);
	}
}
