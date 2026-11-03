package dev.pandesal.resume_reviewer.discord;

import dev.pandesal.resume_reviewer.review.ReviewResult;
import dev.pandesal.resume_reviewer.review.RoleAssessment;
import dev.pandesal.resume_reviewer.review.RoleSuitabilityResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Formats review results into Discord-friendly message chunks (each
 * within Discord's 2000-character limit).
 */
public final class DiscordMessages {

	private static final int MAX = 2000;

	private DiscordMessages() {
	}

	/**
	 * @return a list of message chunks to send in order
	 */
	public static List<String> review(ReviewResult result) {
		List<String> chunks = new ArrayList<>();
		String header = "**Resume Review** — Overall score: **" + (int) result.overallScore() + "/100**";
		chunks.add(header);
		addSection(chunks, "**Strengths**", result.strengths());
		addSection(chunks, "**Critical ATS Blockers**", result.criticalBlockers());
		addSection(chunks, "**Recommendations**", result.recommendations());
		return chunks;
	}

	/**
	 * @return a list of message chunks for the role suitability analysis
	 */
	public static List<String> roleSuitability(RoleSuitabilityResult result) {
		List<String> chunks = new ArrayList<>();
		chunks.add("**Role Suitability**");
		for (RoleAssessment assessment : result.assessments()) {
			StringBuilder section = new StringBuilder()
				.append("**").append(assessment.role()).append("** — **")
				.append((int) assessment.score()).append("/100**");
			if (assessment.reasoning() != null && !assessment.reasoning().isBlank()) {
				section.append('\n').append(assessment.reasoning().trim());
			}
			chunks.addAll(split(section.toString()));
		}
		return chunks;
	}

	private static void addSection(List<String> chunks, String title, String body) {
		if (body == null || body.isBlank()) {
			return;
		}
		String section = title + "\n" + body.trim();
		chunks.addAll(split(section));
	}

	/**
	 * Splits text into chunks of at most {@link #MAX} characters, preferring
	 * line boundaries.
	 */
	public static List<String> split(String text) {
		List<String> chunks = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return chunks;
		}
		StringBuilder current = new StringBuilder();
		for (String line : text.split("\n", -1)) {
			if (current.length() > 0 && current.length() + line.length() + 1 > MAX) {
				chunks.add(current.toString());
				current = new StringBuilder();
			}
			// A single line longer than MAX must be hard-split.
			while (line.length() > MAX) {
				if (current.length() > 0) {
					chunks.add(current.toString());
					current = new StringBuilder();
				}
				chunks.add(line.substring(0, MAX));
				line = line.substring(MAX);
			}
			if (current.length() > 0) {
				current.append('\n');
			}
			current.append(line);
		}
		if (current.length() > 0) {
			chunks.add(current.toString());
		}
		return chunks;
	}
}
