package dev.pandesal.resume_reviewer.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResumeRedactorTest {

	private final ResumeRedactor redactor = new ResumeRedactor();

	@Test
	void redactsNameEmailAndPhones() {
		String text = """
			Juan Dela Cruz
			Software Engineer
			Email: juan.dela.cruz@gmail.com
			Mobile: +63 9123 456789
			Alt: 0912 3456789
			Office: 02 1237899
			""";
		String redacted = redactor.redact(text);

		assertThat(redacted).contains(ResumeRedactor.NAME_PLACEHOLDER);
		assertThat(redacted).doesNotContain("Juan Dela Cruz");
		assertThat(redacted).contains(ResumeRedactor.EMAIL_PLACEHOLDER);
		assertThat(redacted).doesNotContain("juan.dela.cruz@gmail.com");
		assertThat(redacted).contains(ResumeRedactor.PHONE_PLACEHOLDER, ResumeRedactor.PHONE_PLACEHOLDER,
			ResumeRedactor.PHONE_PLACEHOLDER);
		assertThat(redacted).doesNotContain("63 9123", "0912 3456789", "02 1237899");
	}

	@Test
	void phonePrefixMustStartAtWordBoundary() {
		String text = "Order 109123456789 and 10212345678";
		assertThat(redactor.redact(text)).doesNotContain(ResumeRedactor.PHONE_PLACEHOLDER);
	}

	@Test
	void plus63RequiresExactly10Digits() {
		// 10 digits -> redacted
		assertThat(redactor.redact("C: +63 9123 456789"))
			.contains(ResumeRedactor.PHONE_PLACEHOLDER)
			.doesNotContain("+63 9123 456789");
		// 9 digits -> not redacted
		assertThat(redactor.redact("A: +63 9123 45678"))
			.doesNotContain(ResumeRedactor.PHONE_PLACEHOLDER)
			.contains("+63 9123 45678");
		// 11 digits -> not redacted
		assertThat(redactor.redact("B: +63 9123 4567890"))
			.doesNotContain(ResumeRedactor.PHONE_PLACEHOLDER)
			.contains("+63 9123 4567890");
	}

	@Test
	void plus63PrefixVariants() {
		assertThat(redactor.redact("A: 63 9123 456789"))
			.contains(ResumeRedactor.PHONE_PLACEHOLDER)
			.doesNotContain("63 9123 456789");
		assertThat(redactor.redact("B: (+63) 9123 456789"))
			.contains(ResumeRedactor.PHONE_PLACEHOLDER)
			.doesNotContain("(+63) 9123 456789");
		assertThat(redactor.redact("C: (63) 9123 456789"))
			.contains(ResumeRedactor.PHONE_PLACEHOLDER)
			.doesNotContain("(63) 9123 456789");
	}

	@Test
	void zeroTwoPrefixVariants() {
		assertThat(redactor.redact("A: (02) 1234567"))
			.contains(ResumeRedactor.PHONE_PLACEHOLDER)
			.doesNotContain("(02) 1234567");
		assertThat(redactor.redact("B: 02 12345678"))
			.contains(ResumeRedactor.PHONE_PLACEHOLDER)
			.doesNotContain("02 12345678");
	}

	@Test
	void zeroTwoAccepts7Or8Digits() {
		String text = "A: 02 1234567 B: 02 12345678 C: 02 123456789";
		String redacted = redactor.redact(text);
		assertThat(redacted).contains(ResumeRedactor.PHONE_PLACEHOLDER, ResumeRedactor.PHONE_PLACEHOLDER);
		assertThat(redacted).contains("02 123456789");
	}

	@Test
	void doesNotRedactSectionHeadingAsName() {
		String text = """
			PROFESSIONAL SUMMARY
			Juan Dela Cruz
			""";
		String redacted = redactor.redact(text);
		assertThat(redacted).contains("PROFESSIONAL SUMMARY");
		assertThat(redacted).contains(ResumeRedactor.NAME_PLACEHOLDER);
	}

	@Test
	void leavesTextWithoutPiiUnchanged() {
		String text = "Experienced developer with 5 years of Java and Spring.";
		assertThat(redactor.redact(text)).isEqualTo(text);
	}

	@Test
	void removesBlankLinesAndTrimsTrailingWhitespace() {
		String text = """
			Juan Dela Cruz
			
			Software Engineer   
			
			

			Experience
			  - Built things
			""";
		String redacted = redactor.redact(text);

		assertThat(redacted).doesNotContain("\n\n");
		assertThat(redacted).doesNotContain("  \n");
		assertThat(redacted).doesNotStartWith("\n");
		assertThat(redacted).doesNotEndWith("\n");
		assertThat(redacted).isEqualTo("""
			[NAME]
			Software Engineer
			Experience
			  - Built things""");
	}

	@Test
	void normalizeWhitespaceIsIdempotent() {
		String text = "a\n\n\n\nb   \n\nc";
		String once = redactor.normalizeWhitespace(text);
		assertThat(redactor.normalizeWhitespace(once)).isEqualTo(once);
	}
}
