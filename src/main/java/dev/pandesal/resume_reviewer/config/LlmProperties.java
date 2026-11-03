package dev.pandesal.resume_reviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the OpenAI-compatible LLM endpoint.
 *
 * @param baseUrl        base URL of the endpoint, e.g. {@code https://api.example.com/v1}
 * @param apiKey         bearer token / api key
 * @param model          model identifier to request
 * @param temperature    sampling temperature (0.0 - 1.0)
 * @param timeoutSeconds connect/read timeout for LLM requests
 * @param reasoningEffort reasoning effort hint (e.g. {@code low}); blank to omit
 * @param logRequest     when true, the full request body is logged (local debugging)
 * @param logResponse    when true, the full response body is logged (local debugging)
 * @param maxTokens      maximum tokens in the LLM response
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(String baseUrl, String apiKey, String model, double temperature, int timeoutSeconds,
	String reasoningEffort, boolean logRequest, boolean logResponse, int maxTokens) {
}
