package com.reusable.notificator.service.notification;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

@Configuration(proxyBeanMethods = false)
class NotificationQueueConfiguration {

	@Bean
	Declarables notificationQueues(@Value("${notification.queue}") String queueName) {
		Queue failedQueue = QueueBuilder.durable(queueName + ".failed").build();
		Queue notificationQueue = QueueBuilder.durable(queueName)
				.deadLetterExchange("")
				.deadLetterRoutingKey(failedQueue.getName())
				.build();

		return new Declarables(failedQueue, notificationQueue);
	}

	@Bean
	JacksonJsonMessageConverter notificationMessageConverter() {
		JsonMapper mapper = JsonMapper.builder()
				.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.withCoercionConfig(LogicalType.Textual, config -> config
						.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
				.build();
		return new JacksonJsonMessageConverter(mapper);
	}
}
