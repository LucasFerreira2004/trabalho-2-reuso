package com.reusable.notificator.service;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import jakarta.mail.Message.RecipientType;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
		"spring.rabbitmq.listener.simple.auto-startup=false",
		"notification.from=notifications@example.com"
})
class ApplicationTests {

	@Autowired
	private RabbitListenerEndpointRegistry listenerRegistry;

	@MockitoBean
	private JavaMailSender mailSender;

	@Test
	void sendsPlainTextEmailFromJsonWithoutJavaTypeHeaders() throws Exception {
		MimeMessage email = prepareEmail();

		listener().onMessage(json("""
				{
				  "to": ["student@example.com", "teacher@example.com"],
				  "subject": "Reunião",
				  "body": "Olá!\\nSee you tomorrow.",
				  "cc": ["team@example.com"],
				  "bcc": ["archive@example.com"]
				}
				"""), null);

		verify(mailSender).send(email);
		assertThat(email.getFrom()).extracting(Object::toString)
				.containsExactly("notifications@example.com");
		assertThat(email.getRecipients(RecipientType.TO)).extracting(Object::toString)
				.containsExactly("student@example.com", "teacher@example.com");
		assertThat(email.getRecipients(RecipientType.CC)).extracting(Object::toString)
				.containsExactly("team@example.com");
		assertThat(email.getRecipients(RecipientType.BCC)).extracting(Object::toString)
				.containsExactly("archive@example.com");
		assertThat(email.getSubject()).isEqualTo("Reunião");
		assertThat(email.getContent()).isEqualTo("Olá!\nSee you tomorrow.");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{",
			"{\"to\":[],\"subject\":\"Notice\",\"body\":\"Message\"}",
			"{\"to\":[\"invalid\"],\"subject\":\"Notice\",\"body\":\"Message\"}",
			"{\"to\":[\"student@example.com\"],\"subject\":\"Notice\",\"body\":42}"
	})
	void rejectsInvalidNotificationsBeforeSending(String payload) {
		prepareEmail();

		assertThatThrownBy(() -> listener().onMessage(json(payload), null))
				.isInstanceOf(ListenerExecutionFailedException.class);

		verify(mailSender, never()).send(any(MimeMessage.class));
	}

	@Test
	void propagatesSmtpFailureToTheListenerContainer() throws Exception {
		MimeMessage email = prepareEmail();
		doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(email);

		assertThatThrownBy(() -> listener().onMessage(json("""
				{"to":["student@example.com"],"subject":"Notice","body":"Message"}
				"""), null))
				.isInstanceOf(ListenerExecutionFailedException.class)
				.hasRootCauseInstanceOf(MailSendException.class);
	}

	private ChannelAwareMessageListener listener() {
		var container = (AbstractMessageListenerContainer)
				listenerRegistry.getListenerContainer("emailNotificationListener");
		return (ChannelAwareMessageListener) container.getMessageListener();
	}

	private MimeMessage prepareEmail() {
		MimeMessage email = new MimeMessage(Session.getInstance(new Properties()));
		when(mailSender.createMimeMessage()).thenReturn(email);
		return email;
	}

	private Message json(String payload) {
		return MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
				.setContentType(MessageProperties.CONTENT_TYPE_JSON)
				.build();
	}

}
