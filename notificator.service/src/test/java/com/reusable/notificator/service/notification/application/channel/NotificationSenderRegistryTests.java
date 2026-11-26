package com.reusable.notificator.service.notification.application.channel;

import java.util.List;

import com.reusable.notificator.service.notification.model.Channel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationSenderRegistryTests {

	@Test
	void selectsTheSenderRegisteredForTheChannel() {
		var email = sender(Channel.EMAIL);
		var telegram = sender(Channel.TELEGRAM);
		var registry = new NotificationSenderRegistry(List.of(telegram, email));
		assertThat(registry.find(Channel.EMAIL)).containsSame(email);
		assertThat(registry.find(Channel.TELEGRAM)).containsSame(telegram);
		assertThat(new NotificationSenderRegistry(List.of(email)).find(Channel.TELEGRAM)).isEmpty();
	}

	@Test
	void rejectsDuplicateStrategies() {
		assertThatThrownBy(() -> new NotificationSenderRegistry(List.of(sender(Channel.EMAIL), sender(Channel.EMAIL))))
				.isInstanceOf(IllegalStateException.class);
	}

	private NotificationSender sender(Channel channel) {
		var sender = mock(NotificationSender.class);
		when(sender.channel()).thenReturn(channel);
		return sender;
	}
}
