package dev.pandesal.resume_reviewer.review;

import dev.pandesal.resume_reviewer.llm.LlmClient;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM-based role-suitability evaluator. Runs a separate LLM call that scores
 * the resume against each requested job role and parses the response into a
 * {@link RoleSuitabilityResult}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleSuitabilityEvaluator {

	private static final Pattern ROLE_SCORE = Pattern.compile(
		"^\\s*[-*]\\s*(.+?):\\s*(\\d+(?:\\.\\d+)?)\\s*/\\s*100\\s*$");
	private static final Pattern REASONING = Pattern.compile("^\\s*REASONING:\\s*(.*)$");

	private static final String ROLES_PLACEHOLDER = "{{ROLES}}";
	private static final String RESUME_PLACEHOLDER = "{{RESUME}}";

	private final LlmClient llmClient;
	private final PromptLoader promptLoader;

	/**
	 * @param resumeText the extracted resume text
	 * @param roles      the requested roles (already validated and de-duplicated)
	 * @return the parsed per-role assessments
	 * @throws LlmUnavailableException if the LLM call fails or the response
	 *         cannot be parsed
	 */
	public RoleSuitabilityResult evaluate(String resumeText, List<String> roles) {
		String response;
		try {
			response = llmClient.chat(promptLoader.roleSuitabilitySystemPrompt(),
				userPrompt(resumeText, roles));
		}
		catch (Exception e) {
			log.warn("LLM role suitability evaluation failed: {}", e.getMessage());
			throw new LlmUnavailableException(
				"The role suitability analysis cannot be completed at this time (LLM unavailable).", e);
		}
		return parse(response, roles);
	}

	/**
		 * Fills the template placeholders. {@link String#replace(CharSequence, CharSequence)}
		 * replaces every occurrence, so the roles and the resume text are substituted
		 * separately (the resume may itself contain {@code %s}).
		 */
		private String userPrompt(String resumeText, List<String> roles) {
			String prompt = promptLoader.roleSuitabilityUserPrompt()
				.replace(ROLES_PLACEHOLDER, Matcher.quoteReplacement(String.join(", ", roles)));
			return prompt.replace(RESUME_PLACEHOLDER, Matcher.quoteReplacement(resumeText));
		}

	/**
	 * Parses the LLM response into a {@link RoleSuitabilityResult}.
	 *
	 * <p>Roles are matched case-insensitively against the requested roles so
	 * the output keeps the requested order and names. A requested role missing
	 * from the response is reported with a score of 0.
	 *
	 * @throws LlmUnavailableException if no role score can be parsed
	 */
	private RoleSuitabilityResult parse(String response, List<String> roles) {
		if (response == null || response.isBlank()) {
			throw new LlmUnavailableException(
				"The role suitability analysis cannot be completed at this time (empty LLM response).");
		}
		List<RoleAssessment> parsed = new ArrayList<>();
		String currentRole = null;
		double currentScore = 0;
		StringBuilder currentReasoning = new StringBuilder();
		for (String line : response.split("\\R")) {
			Matcher score = ROLE_SCORE.matcher(line);
			if (score.matches()) {
				if (currentRole != null) {
					parsed.add(new RoleAssessment(currentRole, currentScore, currentReasoning.toString().trim()));
				}
				currentRole = score.group(1).trim();
				currentScore = Double.parseDouble(score.group(2));
				currentReasoning = new StringBuilder();
				continue;
			}
			Matcher reasoning = REASONING.matcher(line);
			if (reasoning.matches() && currentRole != null) {
				currentReasoning.append(reasoning.group(1).trim());
			}
		}
		if (currentRole != null) {
			parsed.add(new RoleAssessment(currentRole, currentScore, currentReasoning.toString().trim()));
		}
		if (parsed.isEmpty()) {
			throw new LlmUnavailableException(
				"The role suitability analysis cannot be completed at this time (unparseable LLM response).");
		}

		List<RoleAssessment> ordered = new ArrayList<>();
		for (String role : roles) {
			parsed.stream()
				.filter(a -> a.role().equalsIgnoreCase(role))
				.findFirst()
				.ifPresentOrElse(ordered::add, () -> ordered.add(new RoleAssessment(role, 0, "Not assessed.")));
		}
		return new RoleSuitabilityResult(ordered);
	}
}