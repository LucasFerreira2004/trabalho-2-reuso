package com.reusable.notificator.service.notification;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
class NotificationSenderRegistry {

	private final Map<Channel, NotificationSender> senders = new EnumMap<>(Channel.class);

	NotificationSenderRegistry(List<NotificationSender> candidates) {
		for (var sender : candidates) {
			var channel = sender.channel();
			if (senders.putIfAbsent(channel, sender) != null) {
				throw new IllegalStateException("More than one sender registered for " + channel);
			}
		}
	}

	Optional<NotificationSender> find(Channel channel) {
		return Optional.ofNullable(senders.get(channel));
	}
}
