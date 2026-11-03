package dev.pandesal.resume_reviewer.discord;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeReviewCommandAutoReviewTest {

	@Test
	void acceptsPrefixedPdf() {
		assertThat(ResumeReviewCommand.isAutoReviewFile("FOR_REVIEW_Kyle-Resume.pdf")).isTrue();
		assertThat(ResumeReviewCommand.isAutoReviewFile("FOR_REVIEW_resume.PDF")).isTrue();
	}

	@Test
	void rejectsUnprefixedOrNonPdf() {
		assertThat(ResumeReviewCommand.isAutoReviewFile("Kyle-Resume.pdf")).isFalse();
		assertThat(ResumeReviewCommand.isAutoReviewFile("FOR_REVIEW_resume.docx")).isFalse();
		assertThat(ResumeReviewCommand.isAutoReviewFile("for_review_resume.pdf")).isFalse();
		assertThat(ResumeReviewCommand.isAutoReviewFile(null)).isFalse();
	}

	@Test
	void parsesRolesFromMarkerLine() {
		String content = "Hello guys, this is my resume, please help evaluate!\n\nREVIEW_ROLES: Engineer,QA\n";
		assertThat(ResumeReviewCommand.parseReviewRoles(content)).containsExactly("Engineer", "QA");
	}

	@Test
	void markerMustStartTheLine() {
		assertThat(ResumeReviewCommand.parseReviewRoles("see REVIEW_ROLES: Engineer")).isEmpty();
		assertThat(ResumeReviewCommand.parseReviewRoles("  REVIEW_ROLES: Engineer")).isEmpty();
	}

	@Test
	void markerIsCaseSensitive() {
		assertThat(ResumeReviewCommand.parseReviewRoles("review_roles: Engineer")).isEmpty();
		assertThat(ResumeReviewCommand.parseReviewRoles("Review_Roles: Engineer")).isEmpty();
	}

	@Test
	void absentMarkerYieldsNoRoles() {
		assertThat(ResumeReviewCommand.parseReviewRoles("just a resume")).isEmpty();
		assertThat(ResumeReviewCommand.parseReviewRoles(null)).isEmpty();
		assertThat(ResumeReviewCommand.parseReviewRoles("REVIEW_ROLES:")).isEmpty();
	}

	@Test
	void capsRolesAtFive() {
		assertThat(ResumeReviewCommand.parseReviewRoles("REVIEW_ROLES: a,b,c,d,e,f,g")).hasSize(5);
	}

	@Test
	void usesFirstMarkerLineOnly() {
		String content = "REVIEW_ROLES: Engineer\nREVIEW_ROLES: QA";
		assertThat(ResumeReviewCommand.parseReviewRoles(content)).containsExactly("Engineer");
	}
}