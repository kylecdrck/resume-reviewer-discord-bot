package dev.pandesal.resume_reviewer.llm;

/**
 * Abstraction over an LLM chat-completion endpoint.
 */
public interface LlmClient {

	/**
	 * Sends a chat completion request.
	 *
	 * @param system the system prompt
	 * @param user   the user prompt
	 * @return the model's response content
	 */
	String chat(String system, String user);
}
