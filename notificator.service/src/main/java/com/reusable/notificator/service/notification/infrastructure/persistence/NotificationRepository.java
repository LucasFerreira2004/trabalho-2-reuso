package com.reusable.notificator.service.notification.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.reusable.notificator.service.notification.model.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	@Modifying
	@Transactional
	@Query("update Notification n set n.status = PROCESSING where n.id = :id and n.status = PENDING")
	int claim(UUID id);

	@Modifying
	@Transactional
	@Query("""
			update Notification n set n.attempts = n.attempts + 1
			where n.id = :id and n.status = PROCESSING
			""")
	int recordAttempt(UUID id);

	@Modifying
	@Transactional
	@Query("""
			update Notification n set n.status = :status, n.errorMessage = :errorMessage, n.sentAt = :sentAt
			where n.id = :id and n.status = PROCESSING
			""")
	int finish(UUID id, Status status, String errorMessage, Instant sentAt);

	@Modifying
	@Transactional
	@Query("""
			update Notification n set n.status = FAILED, n.errorMessage = :errorMessage
			where n.id = :id and n.status = PENDING
			""")
	int failPublication(UUID id, String errorMessage);
}
