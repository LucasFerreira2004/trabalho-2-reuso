package com.reusable.notificator.service.notification.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.reusable.notificator.service.notification.model.Channel;
import com.reusable.notificator.service.notification.model.NotificationContent;
import com.reusable.notificator.service.notification.model.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 120)
	private String source;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private Channel channel;

	@Column(nullable = false, length = 320)
	private String recipient;

	@Column(length = 255)
	private String subject;

	@Column(nullable = false, columnDefinition = "text")
	private String message;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private Status status;

	@Column(nullable = false)
	private int attempts;

	private String errorMessage;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant sentAt;

	protected Notification() {
	}

	public Notification(String source, Channel channel, NotificationContent content) {
		this.source = source;
		this.channel = channel;
		this.recipient = content.recipient();
		this.subject = content.subject();
		this.message = content.message();
		this.status = Status.PENDING;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getSource() {
		return source;
	}

	public Channel getChannel() {
		return channel;
	}

	public Status getStatus() {
		return status;
	}

	public int getAttempts() {
		return attempts;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public NotificationContent getContent() {
		return new NotificationContent(recipient, subject, message);
	}
}
