package com.reusable.notificator.service.notification;

public enum ProcessingResult {
	/** The provider accepted the notification and SENT was persisted. */
	SENT,
	/** A prior delivery already recorded a final status. */
	ALREADY_FINALIZED,
	/** An earlier delivery holds the processing claim. */
	ALREADY_PROCESSING,
	/** Processing ended in a definitive failure and FAILED was persisted. */
	FAILED,
	/** The ID refers to no persisted notification. */
	NOT_FOUND
}
