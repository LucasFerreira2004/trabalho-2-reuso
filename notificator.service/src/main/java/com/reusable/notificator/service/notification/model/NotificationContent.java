package com.reusable.notificator.service.notification.model;

/**
 * The provider-facing content of a notification. The subject is optional for Telegram.
 */
public record NotificationContent(String recipient, String subject, String message) {
}
