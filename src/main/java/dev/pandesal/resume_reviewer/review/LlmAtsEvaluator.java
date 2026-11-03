package dev.pandesal.resume_reviewer.review;

import dev.pandesal.resume_reviewer.llm.LlmClient;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM-based ATS evaluator. Builds the system/user prompts, sends them to the
 * LLM, and parses the structured response into a {@link ReviewResult}.
 *
 * <p>The LLM independently scores the resume across four rubrics
 * (Structure &amp; Parsing /25, Contact Information /10, Keywords &amp;
 * Alignment /30, Impact &amp; Metrics /35) and returns the overall score,
 * detailed ratings, critical blockers, strengths, and recommendations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmAtsEvaluator {

	private static final Pattern OVERALL_SCORE = Pattern.compile("OVERALL_ATS_SCORE:\\s*(\\d+(?:\\.\\d+)?)\\s*/\\s*100");
	private static final Pattern DETAILED_RATING = Pattern.compile(
		"[-*]\\s*(Structure & Parsing|Contact Information|Keywords & Alignment|Impact & Metrics):\\s*(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+)");

	private final LlmClient llmClient;
	private final PromptLoader promptLoader;

	/**
	 * @param resumeText the extracted resume text
	 * @return the parsed evaluation result
	 * @throws LlmUnavailableException if the LLM call fails or the response
	 *         cannot be parsed
	 */
	public ReviewResult evaluate(String resumeText) {
		String response;
		try {
			response = llmClient.chat(promptLoader.systemPrompt(), userPrompt(resumeText));
		}
		catch (Exception e) {
			log.warn("LLM evaluation failed: {}", e.getMessage());
			throw new LlmUnavailableException("The evaluation cannot be completed at this time (LLM unavailable).", e);
		}
		return parse(response);
	}

	private String userPrompt(String resumeText) {
			return promptLoader.userPrompt().replace("%s", Matcher.quoteReplacement(resumeText));
	}

	/**
	 * Parses the LLM response into a {@link ReviewResult}.
	 *
		 * <p>Resume text is untrusted and may contain a forged report block. The
		 * model's own answer is the last one in the response, so the last
		 * {@code OVERALL_ATS_SCORE} occurrence anchors the parse.
		 *
		 * @throws LlmUnavailableException if the overall score is missing
		 */
		private ReviewResult parse(String response) {
			if (response == null || response.isBlank()) {
				throw new LlmUnavailableException("The evaluation cannot be completed at this time (empty LLM response).");
		}
			int anchor = response.lastIndexOf("OVERALL_ATS_SCORE:");
			Matcher overall = OVERALL_SCORE.matcher(anchor >= 0 ? response.substring(anchor) : response);
			if (!overall.find()) {
				throw new LlmUnavailableException("The evaluation cannot be completed at this time (unparseable LLM response).");
			}
			double score = Double.parseDouble(overall.group(1));
			String detailedRatings = detailedRatings(response);
			String criticalBlockers = section(response, "CRITICAL_BLOCKERS");
			String strengths = section(response, "STRENGTHS");
			String recommendations = section(response, "RECOMMENDATIONS");
			return new ReviewResult(score, detailedRatings, criticalBlockers, strengths, recommendations,
				buildReport(score, detailedRatings, criticalBlockers, strengths, recommendations));
		}

	/**
	 * Extracts the DETAILED_RATINGS block (the four per-category score lines).
		 * Uses the last occurrence, matching {@link #parse(String)}.
		 */
		private String detailedRatings(String response) {
			int start = response.lastIndexOf("DETAILED_RATINGS:");
		if (start < 0) {
			return "";
		}
		int contentStart = start + "DETAILED_RATINGS:".length();
		int nextHeader = -1;
		for (String h : List.of("CRITICAL_BLOCKERS", "STRENGTHS", "RECOMMENDATIONS")) {
			int idx = response.indexOf(h + ":", contentStart);
			if (idx >= 0 && (nextHeader < 0 || idx < nextHeader)) {
				nextHeader = idx;
			}
		}
		String content = nextHeader >= 0
			? response.substring(contentStart, nextHeader)
			: response.substring(contentStart);
		StringBuilder sb = new StringBuilder();
		Matcher m = DETAILED_RATING.matcher(content);
		while (m.find()) {
			sb.append("- ").append(m.group(1)).append(": ").append(m.group(2)).append("/").append(m.group(3)).append('\n');
		}
		return sb.toString().trim();
	}

	/**
		 * Extracts a named section's content (up to the next known header). Uses
		 * the last occurrence, matching {@link #parse(String)}.
		 */
		private String section(String response, String header) {
			int start = response.lastIndexOf(header + ":");
		if (start < 0) {
			return "";
		}
		int contentStart = start + header.length() + 1;
		int nextHeader = -1;
		for (String h : List.of("OVERALL_ATS_SCORE", "DETAILED_RATINGS", "CRITICAL_BLOCKERS", "STRENGTHS", "RECOMMENDATIONS")) {
			if (h.equals(header)) {
				continue;
			}
			int idx = response.indexOf(h + ":", contentStart);
			if (idx >= 0 && (nextHeader < 0 || idx < nextHeader)) {
				nextHeader = idx;
			}
		}
		String content = nextHeader >= 0
			? response.substring(contentStart, nextHeader)
			: response.substring(contentStart);
		return content.trim();
	}

	private String buildReport(double score, String detailedRatings, String criticalBlockers, String strengths,
		String recommendations) {
		return "# ATS Resume Evaluation Report\n\n"
			+ "## Summary\n"
			+ "* **Overall Score:** " + (int) score + "/100\n\n"
			+ "## Detailed Ratings\n"
			+ (detailedRatings.isEmpty() ? "(none)\n" : detailedRatings + "\n") + "\n"
			+ "## Critical ATS Blockers\n"
			+ (criticalBlockers.isEmpty() ? "(none)\n" : criticalBlockers + "\n") + "\n"
			+ "## Key Strengths\n"
			+ (strengths.isEmpty() ? "(none)\n" : strengths + "\n") + "\n"
			+ "## Actionable Recommendations\n"
			+ (recommendations.isEmpty() ? "(none)\n" : recommendations + "\n");
	}
}
