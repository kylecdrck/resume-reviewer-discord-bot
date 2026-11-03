package dev.pandesal.resume_reviewer.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import dev.pandesal.resume_reviewer.config.LlmProperties;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OpenAI-compatible chat-completions client ({@code POST {baseUrl}/chat/completions}).
 */
@Slf4j
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

	private final LlmProperties props;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public OpenAiCompatibleLlmClient(LlmProperties props, ObjectMapper objectMapper) {
		this.props = props;
		this.objectMapper = objectMapper;
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(props.timeoutSeconds()));
		requestFactory.setReadTimeout(Duration.ofSeconds(props.timeoutSeconds()));
		this.restClient = RestClient.builder()
			.requestFactory(requestFactory)
			.baseUrl(props.baseUrl())
			.defaultHeader("Authorization", "Bearer " + props.apiKey())
			.build();
	}

	@Override
	public String chat(String system, String user) {
		ObjectNode body = objectMapper.createObjectNode();
		body.put("model", props.model());
		body.put("temperature", props.temperature());
		if (props.reasoningEffort() != null && !props.reasoningEffort().isBlank()) {
			body.put("reasoning_effort", props.reasoningEffort());
		}
		ArrayNode messages = body.putArray("messages");
		messages.addObject().put("role", "system").put("content", system);
		messages.addObject().put("role", "user").put("content", user);
		body.put("max_tokens", props.maxTokens());
		return send(body);
	}

	private String send(ObjectNode body) {
		long start = System.currentTimeMillis();
		log.info("LLM request to {} (model {}, reasoning_effort={}, max_tokens={})", props.baseUrl(),
			props.model(), props.reasoningEffort(), body.path("max_tokens").asInt());
		if (props.logRequest()) {
			log.info("LLM request body: {}", body.toString());
		}

		JsonNode response = restClient.post()
			.uri("/chat/completions")
			.body(body)
			.retrieve()
			.body(JsonNode.class);

		log.info("LLM response received in {} ms", System.currentTimeMillis() - start);
		if (props.logResponse()) {
			log.info("LLM response: {}", response);
		}
		if (response == null || !response.path("choices").isArray() || response.get("choices").isEmpty()) {
			throw new IllegalStateException("Unexpected LLM response: " + response);
		}
		return response.path("choices").get(0).path("message").path("content").asText();
	}
}
