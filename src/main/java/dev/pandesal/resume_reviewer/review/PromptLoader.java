package dev.pandesal.resume_reviewer.review;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads prompt templates from the classpath once at startup.
 */
@Component
public class PromptLoader {

	private final String systemPrompt;
	private final String userPrompt;
		private final String roleSuitabilitySystemPrompt;
		private final String roleSuitabilityUserPrompt;

		public PromptLoader() {
			this.systemPrompt = load("prompts/system-prompt.txt");
			this.userPrompt = load("prompts/user-prompt.txt");
			this.roleSuitabilitySystemPrompt = load("prompts/role-suitability-system-prompt.txt");
			this.roleSuitabilityUserPrompt = load("prompts/role-suitability-user-prompt.txt");
	}

		public String systemPrompt() {
			return systemPrompt;
	}

		public String userPrompt() {
			return userPrompt;
		}

		public String roleSuitabilitySystemPrompt() {
			return roleSuitabilitySystemPrompt;
		}

		public String roleSuitabilityUserPrompt() {
			return roleSuitabilityUserPrompt;
		}

	private static String load(String path) {
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Failed to load prompt template: " + path, e);
		}
	}
}
