package dev.pandesal.resume_reviewer.discord;

import dev.pandesal.resume_reviewer.review.RoleAssessment;
import dev.pandesal.resume_reviewer.review.RoleSuitabilityResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordMessagesRoleSuitabilityTest {

	@Test
	void formatsHeaderAndOneSectionPerRole() {
		var result = new RoleSuitabilityResult(List.of(
			new RoleAssessment("QA", 80, "Strong testing background."),
			new RoleAssessment("Tester", 0, "Not assessed.")));

		List<String> chunks = DiscordMessages.roleSuitability(result);

		assertThat(chunks).hasSize(3);
		assertThat(chunks.get(0)).isEqualTo("**Role Suitability**");
		assertThat(chunks.get(1)).isEqualTo("**QA** — **80/100**\nStrong testing background.");
		assertThat(chunks.get(2)).isEqualTo("**Tester** — **0/100**\nNot assessed.");
	}

	@Test
	void omitsBlankReasoning() {
		var result = new RoleSuitabilityResult(List.of(new RoleAssessment("QA", 55, " ")));

		assertThat(DiscordMessages.roleSuitability(result)).containsExactly("**Role Suitability**", "**QA** — **55/100**");
	}
}