package dev.pandesal.resume_reviewer.pdf;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Cleans extracted resume text: removes unrecognizable characters (control
 * codes, zero-width characters, private-use-area glyphs from embedded PDF
 * fonts, symbols/pictographs) and normalizes whitespace.
 *
 * <p>Kept: printable ASCII, Latin-1/Latin-Extended letters and digits, and
 * general punctuation (curly quotes, en/em dashes, bullets).
 */
@Component
public class TextSanitizer {

	/**
	 * @param text raw extracted text (may be null)
	 * @return sanitized text with only recognizable characters
	 */
	public String sanitize(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		StringBuilder kept = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (isKept(c)) {
				kept.append(c);
			}
		}
		return normalizeWhitespace(kept.toString());
	}

	private boolean isKept(char c) {
		if (c == '\n' || c == '\t') {
			return true;
		}
		// Zero-width characters are always dropped.
		if (c == 0x200B || c == 0x200C || c == 0x200D || c == 0xFEFF) {
			return false;
		}
		// Printable ASCII
		if (c >= 0x20 && c <= 0x7E) {
			return true;
		}
		// Latin-1 supplement (accented letters, nbsp, etc.)
		if (c >= 0xA0 && c <= 0xFF) {
			return true;
		}
		// Latin Extended A/B
		if (c >= 0x100 && c <= 0x1FFF) {
			return true;
		}
		// General punctuation: curly quotes, dashes, ellipsis, bullets
		if (c >= 0x2000 && c <= 0x206F) {
			return true;
		}
		// Everything else (symbols, pictographs, CJK, private use area,
		// zero-width chars, control codes) is dropped.
		return false;
	}

	/**
	 * Collapses runs of spaces/tabs and trims trailing whitespace per line.
	 */
	private String normalizeWhitespace(String text) {
		List<String> lines = new ArrayList<>();
		for (String line : text.split("\n", -1)) {
			String cleaned = line.replaceAll("[ \\t]{2,}", " ").replaceAll("[ \\t]+$", "");
			lines.add(cleaned);
		}
		return String.join("\n", lines);
	}
}
