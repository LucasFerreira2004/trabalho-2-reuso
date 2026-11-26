package com.reusable.notificator.service.notification;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class EmailNotificationSender implements NotificationSender {

	private final JavaMailSender mailSender;
	private final String from;

	EmailNotificationSender(JavaMailSender mailSender, @Value("${notification.from}") String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public Channel channel() {
		return Channel.EMAIL;
	}

	@Override
	public void send(NotificationContent content) {
		try {
			send(new EmailNotificationRequest(List.of(content.recipient()), content.subject(), content.message(),
					null, null));
		} catch (MailSendException failure) {
			boolean retryable = isTemporarySmtpFailure(failure)
					|| Arrays.stream(failure.getMessageExceptions()).anyMatch(this::isTemporarySmtpFailure);
			throw new NotificationDeliveryException(retryable
					? "SMTP is temporarily unavailable." : "SMTP rejected the email.", retryable);
		} catch (MailException | IllegalArgumentException failure) {
			throw new NotificationDeliveryException("SMTP could not send the email.", false);
		}
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

	private boolean isTemporarySmtpFailure(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SMTPAddressFailedException smtp) {
				return smtp.getReturnCode() >= 400 && smtp.getReturnCode() < 500;
			}
			if (cause instanceof SMTPSendFailedException smtp) {
				return smtp.getReturnCode() >= 400 && smtp.getReturnCode() < 500;
			}
			if (cause instanceof SocketException || cause instanceof SocketTimeoutException
					|| cause instanceof UnknownHostException) {
				return true;
			}
		}
		return false;
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
