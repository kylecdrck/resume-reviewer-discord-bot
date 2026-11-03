package dev.pandesal.resume_reviewer.pdf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextSanitizerTest {

	private final TextSanitizer sanitizer = new TextSanitizer();

	@Test
	void keepsPrintableAsciiAndLatin() {
		assertThat(sanitizer.sanitize("John Doe\nJava, Spring Boot\n+63 917 123 4567"))
			.contains("John Doe", "Java, Spring Boot", "+63 917 123 4567");
	}

	@Test
	void keepsAccentedAndPunctuation() {
		assertThat(sanitizer.sanitize("José — “quoted” • bullet")).contains("José", "—", "“quoted”", "•");
	}

	@Test
	void stripsControlAndZeroWidthChars() {
		assertThat(sanitizer.sanitize("ab\u0000cd\u200bef\u0007gh")).isEqualTo("abcdefgh");
	}

	@Test
	void stripsPrivateUseAreaGlyphs() {
		// Common in PDFs with embedded symbol fonts
		assertThat(sanitizer.sanitize("Java\uE000\uE001Spring")).isEqualTo("JavaSpring");
	}

	@Test
	void stripsPictographsAndSymbols() {
		assertThat(sanitizer.sanitize("Email: \u2709\ufe0f Phone: \u260E")).contains("Email:", "Phone:");
	}

	@Test
	void collapsesSpaceRunsAndTrimsLineEnds() {
		assertThat(sanitizer.sanitize("a    b   \nc\t\t")).isEqualTo("a b\nc");
	}

	@Test
	void nullAndEmpty() {
		assertThat(sanitizer.sanitize(null)).isEmpty();
		assertThat(sanitizer.sanitize("")).isEmpty();
	}
}
