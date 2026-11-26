package com.reusable.notificator.service.notification;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/notifications")
class NotificationController {

	private final NotificationService service;

	NotificationController(NotificationService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "Submit a notification for asynchronous processing")
	@ApiResponse(responseCode = "202", description = "Notification accepted")
	@ApiResponse(responseCode = "400", description = "Invalid notification", content = @Content)
	@ApiResponse(responseCode = "503", description = "Notification could not be accepted", content = @Content)
	NotificationService.Acceptance submit(@RequestBody SubmitNotificationRequest request) {
		return service.submit(request);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Look up a notification by ID")
	@ApiResponse(responseCode = "200", description = "Notification found")
	@ApiResponse(responseCode = "404", description = "Notification not found", content = @Content)
	ResponseEntity<NotificationDetails> find(@PathVariable UUID id) {
		return ResponseEntity.of(service.find(id).map(NotificationDetails::from));
	}

	@ExceptionHandler(NotificationService.InvalidRequest.class)
	ProblemDetail invalidNotification(NotificationService.InvalidRequest failure) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, failure.getMessage());
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
	ProblemDetail malformedRequest() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid JSON request or notification ID.");
	}

	@ExceptionHandler(NotificationPublicationException.class)
	ProblemDetail publicationFailure() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Notification could not be queued.");
	}

	record NotificationDetails(UUID id, String source, Channel channel, Status status, int attempts,
			Instant createdAt, Instant sentAt, String errorMessage) {

		static NotificationDetails from(Notification notification) {
			return new NotificationDetails(notification.getId(), notification.getSource(), notification.getChannel(),
					notification.getStatus(), notification.getAttempts(), notification.getCreatedAt(),
					notification.getSentAt(), notification.getErrorMessage());
		}
	}
}
