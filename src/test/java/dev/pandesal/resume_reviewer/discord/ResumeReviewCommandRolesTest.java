package dev.pandesal.resume_reviewer.discord;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeReviewCommandRolesTest {

	@Test
	void parsesCommaSeparatedRoles() {
		assertThat(ResumeReviewCommand.parseRoles("software engineer,QA,tester"))
			.containsExactly("software engineer", "QA", "tester");
	}

	@Test
	void trimsAndDropsBlanks() {
		assertThat(ResumeReviewCommand.parseRoles(" QA , , tester ,"))
			.containsExactly("QA", "tester");
	}

	@Test
	void deduplicatesCaseInsensitivelyKeepingFirstOccurrence() {
		assertThat(ResumeReviewCommand.parseRoles("QA,qa, Qa ,Tester"))
			.containsExactly("QA", "Tester");
	}

	@Test
	void nullOrBlankYieldsNoRoles() {
		assertThat(ResumeReviewCommand.parseRoles(null)).isEmpty();
		assertThat(ResumeReviewCommand.parseRoles("  ,  ")).isEmpty();
	}

	@Test
	void keepsMoreThanFiveRolesForCallerToReject() {
		assertThat(ResumeReviewCommand.parseRoles("a,b,c,d,e,f")).hasSize(6);
	}
}