package com.reusable.notificator.service.notification;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class EmailNotificationSender {

	private final JavaMailSender mailSender;
	private final String from;

	EmailNotificationSender(JavaMailSender mailSender, @Value("${notification.from}") String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	void send(EmailNotificationRequest request) {
		if (request.to() == null || request.to().isEmpty()) {
			throw new IllegalArgumentException("At least one recipient is required.");
		}
		if (!StringUtils.hasText(request.subject())
				|| request.subject().contains("\r") || request.subject().contains("\n")) {
			throw new IllegalArgumentException("The subject must be a non-blank, single line of text.");
		}
		if (!StringUtils.hasText(request.body())) {
			throw new IllegalArgumentException("The body must contain text.");
		}

		MimeMessage message = mailSender.createMimeMessage();
		try {
			MimeMessageHelper email = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
			email.setValidateAddresses(true);
			email.setFrom(from);
			email.setTo(recipientAddresses(request.to()));
			email.setCc(recipientAddresses(request.cc()));
			email.setBcc(recipientAddresses(request.bcc()));
			email.setSubject(request.subject());
			email.setText(request.body());
		} catch (MessagingException exception) {
			throw new IllegalArgumentException("The notification could not be converted to an email.", exception);
		}

		mailSender.send(message);
	}

	private String[] recipientAddresses(List<String> recipients) {
		if (recipients == null) {
			return new String[0];
		}
		if (recipients.stream().anyMatch(address -> !StringUtils.hasText(address))) {
			throw new IllegalArgumentException("Recipient addresses must contain text.");
		}
		return recipients.toArray(String[]::new);
	}
}
