package com.reusable.notificator.service.notification.infrastructure.persistence;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import com.reusable.notificator.service.notification.model.Channel;
import com.reusable.notificator.service.notification.model.NotificationContent;
import com.reusable.notificator.service.notification.model.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class NotificationRepositoryTests {

	@Autowired
	private NotificationRepository repository;

	@Test
	void persistsNotificationAndAllowsOnlyOneProcessingClaim() {
		var content = new NotificationContent("reader@example.com", "Reminder", "Your book is due.");
		var id = repository.save(new Notification("integration-test", Channel.EMAIL, content)).getId();
		try {
			var saved = repository.findById(id).orElseThrow();
			assertThat(saved.getStatus()).isEqualTo(Status.PENDING);
			assertThat(saved.getContent()).isEqualTo(content);
			assertThat(saved.getCreatedAt()).isNotNull();

			var firstClaim = CompletableFuture.supplyAsync(() -> repository.claim(id));
			var secondClaim = CompletableFuture.supplyAsync(() -> repository.claim(id));
			assertThat(firstClaim.join() + secondClaim.join()).isEqualTo(1);
			assertThat(repository.recordAttempt(id)).isEqualTo(1);
			assertThat(repository.finish(id, Status.SENT, null, Instant.now())).isEqualTo(1);

			var finished = repository.findById(id).orElseThrow();
			assertThat(finished.getStatus()).isEqualTo(Status.SENT);
			assertThat(finished.getAttempts()).isEqualTo(1);
			assertThat(finished.getSentAt()).isNotNull();
			assertThat(repository.claim(id)).isZero();
		} finally {
			repository.deleteById(id);
		}
	}
}
