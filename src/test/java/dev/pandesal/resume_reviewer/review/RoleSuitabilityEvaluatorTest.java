package dev.pandesal.resume_reviewer.review;

import dev.pandesal.resume_reviewer.llm.LlmClient;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleSuitabilityEvaluatorTest {

	private static final String SYSTEM = "system";
	private static final String USER = "roles=%s resume=%s";

	private static RoleSuitabilityEvaluator evaluator(String response) {
			return evaluator(response, USER);
		}

		private static RoleSuitabilityEvaluator evaluator(String response, String userTemplate) {
			LlmClient client = (system, user) -> response;
			PromptLoader loader = new PromptLoader() {
				@Override
				public String roleSuitabilitySystemPrompt() {
					return SYSTEM;
				}

				@Override
				public String roleSuitabilityUserPrompt() {
					return userTemplate;
				}
			};
			return new RoleSuitabilityEvaluator(client, loader);
		}

	@Test
	void parsesScoresAndReasoningInRequestedOrder() {
		String response = """
			ROLE_SUITABILITY:
			- QA: 80/100
			  REASONING: Strong testing background.
			- Software Engineer: 70/100
			  REASONING: Solid coding skills.
			""";
		RoleSuitabilityResult result = evaluator(response).evaluate("resume", List.of("Software Engineer", "QA"));

		assertThat(result.assessments()).extracting(RoleAssessment::role)
			.containsExactly("Software Engineer", "QA");
		assertThat(result.assessments()).extracting(RoleAssessment::score).containsExactly(70.0, 80.0);
		assertThat(result.assessments().get(1).reasoning()).isEqualTo("Strong testing background.");
	}

	@Test
	void missingRoleIsReportedAsNotAssessed() {
		String response = "ROLE_SUITABILITY:\n- QA: 80/100\n  REASONING: Good.";
		RoleSuitabilityResult result = evaluator(response).evaluate("resume", List.of("QA", "Tester"));

		assertThat(result.assessments()).extracting(RoleAssessment::role).containsExactly("QA", "Tester");
		assertThat(result.assessments().get(1).score()).isZero();
		assertThat(result.assessments().get(1).reasoning()).isEqualTo("Not assessed.");
	}

	@Test
		void resumeTextIsSentInTheUserPrompt() {
			StringBuilder sent = new StringBuilder();
			LlmClient client = (system, user) -> {
				sent.append(user);
				return "ROLE_SUITABILITY:\n- QA: 80/100\n  REASONING: Good.";
			};
			RoleSuitabilityEvaluator evaluator = new RoleSuitabilityEvaluator(client, new PromptLoader());

			evaluator.evaluate("Java Developer\nSpring Boot, 5 years", List.of("QA"));

			assertThat(sent.toString()).contains("Java Developer\nSpring Boot, 5 years");
			assertThat(sent.toString()).contains("QA");
		}

		@Test
		void resumeTextWithPercentSignsIsNotReformatted() {
			StringBuilder sent = new StringBuilder();
			LlmClient client = (system, user) -> {
				sent.append(user);
				return "ROLE_SUITABILITY:\n- QA: 80/100\n  REASONING: Good.";
			};
			RoleSuitabilityEvaluator evaluator = new RoleSuitabilityEvaluator(client, new PromptLoader());

			evaluator.evaluate("Improved latency by 40% and 100% uptime", List.of("QA"));

			assertThat(sent.toString()).contains("Improved latency by 40% and 100% uptime");
		}

		@Test
		void unparseableResponseThrows() {
		assertThatThrownBy(() -> evaluator("no scores here").evaluate("resume", List.of("QA")))
			.isInstanceOf(LlmUnavailableException.class);
	}

	@Test
	void llmFailureThrows() {
		LlmClient failing = (system, user) -> {
			throw new IllegalStateException("boom");
		};
		RoleSuitabilityEvaluator evaluator = new RoleSuitabilityEvaluator(failing, new PromptLoader());
		assertThatThrownBy(() -> evaluator.evaluate("resume", List.of("QA")))
			.isInstanceOf(LlmUnavailableException.class);
	}
}