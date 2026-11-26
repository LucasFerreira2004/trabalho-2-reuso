package com.reusable.notificator.service.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class NotificationSubmissionTests {

	private final List<UUID> createdIds = new ArrayList<>();

	@Autowired
	private NotificationService service;

	@Autowired
	private NotificationRepository repository;

	@MockitoBean
	private NotificationPublisher publisher;

	@AfterEach
	void removeCreatedNotifications() {
		repository.deleteAllById(createdIds);
	}

	@Test
	void commitsPendingBeforePublishing() {
		doAnswer(call -> {
			UUID id = call.getArgument(0);
			createdIds.add(id);
			var persisted = CompletableFuture.supplyAsync(() -> repository.findById(id)).join().orElseThrow();
			assertThat(persisted.getStatus()).isEqualTo(Status.PENDING);
			return null;
		}).when(publisher).publish(any());

		var acceptance = service.submit(request("student@example.com"));

		assertThat(acceptance.status()).isEqualTo(Status.PENDING);
		assertThat(createdIds).containsExactly(acceptance.id());
	}

	@Test
	void knownPublicationFailureIsPersistedWithoutProviderAttempts() {
		doAnswer(call -> {
			createdIds.add(call.getArgument(0));
			throw new NotificationPublicationException("Broker detail");
		}).when(publisher).publish(any());

		assertThatThrownBy(() -> service.submit(request("student@example.com")))
				.isInstanceOf(NotificationPublicationException.class);

		var failed = repository.findById(createdIds.getFirst()).orElseThrow();
		assertThat(failed.getStatus()).isEqualTo(Status.FAILED);
		assertThat(failed.getAttempts()).isZero();
		assertThat(failed.getErrorMessage()).isEqualTo("Notification could not be queued.");
	}

	@Test
	void invalidRequestsReachNeitherPersistenceNorPublisher() {
		var count = repository.count();
		assertThatThrownBy(() -> service.submit(request("invalid")))
				.isInstanceOf(NotificationService.InvalidRequest.class);
		assertThat(repository.count()).isEqualTo(count);
		verifyNoInteractions(publisher);
	}

	private SubmitNotificationRequest request(String address) {
		return new SubmitNotificationRequest("integration-test", Channel.EMAIL, address, "Reminder", "Test message.");
	}
}
