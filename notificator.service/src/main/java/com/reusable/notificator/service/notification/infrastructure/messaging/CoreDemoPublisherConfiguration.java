package com.reusable.notificator.service.notification.infrastructure.messaging;

import com.reusable.notificator.service.notification.application.processing.NotificationProcessingException;
import com.reusable.notificator.service.notification.application.processing.NotificationProcessor;
import com.reusable.notificator.service.notification.application.submission.NotificationPublicationException;
import com.reusable.notificator.service.notification.application.submission.NotificationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@Profile("core-demo")
class CoreDemoPublisherConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(CoreDemoPublisherConfiguration.class);

	@Bean
	ThreadPoolTaskExecutor coreDemoExecutor() {
		var executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(32);
		executor.setThreadNamePrefix("core-demo-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(15);
		return executor;
	}

	@Bean
	@Primary
	NotificationPublisher coreDemoPublisher(@Qualifier("coreDemoExecutor") ThreadPoolTaskExecutor executor,
			NotificationProcessor processor) {
		logger.info("core-demo is active: notifications are published to an in-process executor.");
		return id -> {
			try {
				executor.execute(() -> {
					try {
						processor.process(id);
					} catch (NotificationProcessingException failure) {
						logger.warn("Could not process notification {} in core-demo.", id);
					}
				});
			} catch (TaskRejectedException failure) {
				throw new NotificationPublicationException("Local publisher could not accept the notification.", failure);
			}
		};
	}
}
