package com.reusable.notificator.service.notification;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "notification.telegram.bot-token="})
class NotificationControllerTests {

	private static final String EMAIL_REQUEST = """
			{"source":"integration-test","channel":"EMAIL","recipient":"reader@example.com",
			"subject":"Reminder","message":"Your book is due."}
			""";

	private final JsonMapper json = JsonMapper.builder().build();
	private final ConcurrentLinkedQueue<UUID> createdIds = new ConcurrentLinkedQueue<>();

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private NotificationRepository repository;

	@MockitoBean
	private NotificationPublisher publisher;

	@AfterEach
	void removeCreatedNotifications() {
		repository.deleteAllById(createdIds);
	}

	@Test
	void acceptsAndFindsNotifications() throws Exception {
		doAnswer(call -> {
			createdIds.add(call.getArgument(0));
			return null;
		}).when(publisher).publish(any());

		var posted = post(EMAIL_REQUEST);
		assertThat(posted.statusCode()).isEqualTo(202);
		var accepted = json.readTree(posted.body());
		assertThat(accepted.path("status").asText()).isEqualTo("PENDING");

		var retrieved = get("/notifications/" + accepted.path("id").asText());
		assertThat(retrieved.statusCode()).isEqualTo(200);
		var details = json.readTree(retrieved.body());
		assertThat(details.path("status").asText()).isEqualTo("PENDING");
		assertThat(details.path("attempts").asInt()).isZero();
		assertThat(details.path("createdAt").asText()).isNotBlank();
	}

	@Test
	void rejectsInvalidInputAndReportsUnknownIds() throws Exception {
		assertThat(post("{").statusCode()).isEqualTo(400);
		assertThat(post(EMAIL_REQUEST.replace("\"EMAIL\"", "0")).statusCode()).isEqualTo(400);
		assertThat(post(EMAIL_REQUEST.replace("reader@example.com", "invalid")).statusCode()).isEqualTo(400);
		assertThat(get("/notifications/not-a-uuid").statusCode()).isEqualTo(400);
		assertThat(get("/notifications/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
		verifyNoInteractions(publisher);
	}

	@Test
	void reportsPublicationFailureWithoutInternalDetails() throws Exception {
		doAnswer(call -> {
			createdIds.add(call.getArgument(0));
			throw new NotificationPublicationException("Internal publisher detail");
		}).when(publisher).publish(any());

		var response = post(EMAIL_REQUEST);
		assertThat(response.statusCode()).isEqualTo(503);
		assertThat(response.body()).doesNotContain("Internal publisher detail");
		assertThat(repository.findById(createdIds.element()).orElseThrow().getStatus()).isEqualTo(Status.FAILED);
	}

	@Test
	void unconfiguredTelegramDoesNotPublish() throws Exception {
		var response = post("""
				{"source":"integration-test","channel":"TELEGRAM","recipient":"-1234","message":"Test"}
				""");
		assertThat(response.statusCode()).isEqualTo(503);
		verifyNoInteractions(publisher);
	}

	@Test
	void exposesBothEndpointsInOpenApi() throws Exception {
		var response = get("/v3/api-docs");
		assertThat(response.statusCode()).isEqualTo(200);
		var paths = json.readTree(response.body()).path("paths");
		assertThat(paths.path("/notifications").has("post")).isTrue();
		assertThat(paths.path("/notifications/{id}").has("get")).isTrue();
		assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
	}

	private HttpResponse<String> get(String path) throws Exception {
		return send(request(path).GET().build());
	}

	private HttpResponse<String> post(String body) throws Exception {
		return send(request("/notifications").header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build());
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
	}

	private HttpResponse<String> send(HttpRequest request) throws Exception {
		try (var client = HttpClient.newHttpClient()) {
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}
}
