package dev.pandesal.resume_reviewer.redaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Redacts recognizable PII from resume text before storage:
 * the owner's name, email addresses, and Philippine phone numbers.
 */
@Component
public class ResumeRedactor {

	public static final String NAME_PLACEHOLDER = "[NAME]";
	public static final String EMAIL_PLACEHOLDER = "[EMAIL]";
	public static final String PHONE_PLACEHOLDER = "[PHONE]";

	private static final Pattern EMAIL = Pattern.compile(
		"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");

	// Prefix must start at a word boundary (blank or start of text);
	// separators (spaces, dashes, dots) between digits are ignored.
	// +63 variants: +63, 63, (+63), (63). 02 variants: 02, (02).
	private static final Pattern PHONE_PLUS63 = Pattern.compile(
		"(?<=^|\\s)(?:\\+?63|\\(\\+?63\\))(?:[\\s\\-.]?\\d){10}(?!\\d)");
	private static final Pattern PHONE_09 = Pattern.compile(
		"(?<=^|\\s)09(?:[\\s\\-.]?\\d){9}(?!\\d)");
	private static final Pattern PHONE_02 = Pattern.compile(
		"(?<=^|\\s)(?:02|\\(02\\))(?:[\\s\\-.]?\\d){7,8}(?!\\d)");

	private static final Pattern NAME_LINE = Pattern.compile(
		"^[A-Za-z][A-Za-z.'\\- ]{1,60}$");
	private static final Set<String> NAME_STOP_WORDS = Set.of(
		"resume", "curriculum", "vitae", "cv", "professional", "summary",
		"profile", "objective", "experience", "skills", "education");

	/**
	 * @param text extracted resume text
	 * @return the text with PII replaced by placeholders and whitespace normalized
	 */
	public String redact(String text) {
		String redacted = redactName(text);
		redacted = EMAIL.matcher(redacted).replaceAll(EMAIL_PLACEHOLDER);
		redacted = PHONE_PLUS63.matcher(redacted).replaceAll(PHONE_PLACEHOLDER);
		redacted = PHONE_09.matcher(redacted).replaceAll(PHONE_PLACEHOLDER);
		redacted = PHONE_02.matcher(redacted).replaceAll(PHONE_PLACEHOLDER);
		return normalizeWhitespace(redacted);
	}

	/**
	 * Trims trailing whitespace on each line and removes all blank lines.
	 */
	public String normalizeWhitespace(String text) {
		List<String> lines = new ArrayList<>();
		for (String line : text.split("\\R", -1)) {
			String trimmed = line.replaceAll("\\s+$", "");
			if (!trimmed.isEmpty()) {
				lines.add(trimmed);
			}
		}
		return String.join("\n", lines);
	}

	private String redactName(String text) {
		String[] lines = text.split("\\R", -1);
		int nonEmpty = 0;
		for (int i = 0; i < lines.length && nonEmpty < 3; i++) {
			String line = lines[i].trim();
			if (line.isEmpty()) {
				continue;
			}
			nonEmpty++;
			if (looksLikeName(line)) {
				lines[i] = NAME_PLACEHOLDER;
				return String.join("\n", lines);
			}
		}
		return text;
	}

	private boolean looksLikeName(String line) {
		if (!NAME_LINE.matcher(line).matches()) {
			return false;
		}
		for (String word : line.toLowerCase(Locale.ROOT).split("[^a-z']+")) {
			if (NAME_STOP_WORDS.contains(word)) {
				return false;
			}
		}
		return true;
	}
}
