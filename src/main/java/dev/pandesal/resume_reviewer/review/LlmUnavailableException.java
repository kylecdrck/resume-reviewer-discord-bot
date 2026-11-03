package dev.pandesal.resume_reviewer.review;

/**
 * Thrown when the LLM is unavailable or its response cannot be parsed, so the
 * review cannot be completed.
 */
public class LlmUnavailableException extends RuntimeException {

	public LlmUnavailableException(String message) {
		super(message);
	}

	public LlmUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
