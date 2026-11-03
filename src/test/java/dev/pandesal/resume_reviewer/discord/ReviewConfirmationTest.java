package dev.pandesal.resume_reviewer.discord;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewConfirmationTest {

	@Test
	void parseKeyExtractsUuidForContinueButton() {
		String key = "abc-123";
		String componentId = ReviewConfirmation.BUTTON_PREFIX + "continue_" + key;
		assertThat(ReviewConfirmation.parseKey(componentId)).isEqualTo(key);
		assertThat(ReviewConfirmation.parseChoice(componentId)).isEqualTo(ReviewConfirmation.Choice.CONTINUE);
	}

	@Test
	void parseKeyExtractsUuidForCancelButton() {
		String key = "abc-123";
		String componentId = ReviewConfirmation.BUTTON_PREFIX + "cancel_" + key;
		assertThat(ReviewConfirmation.parseKey(componentId)).isEqualTo(key);
		assertThat(ReviewConfirmation.parseChoice(componentId)).isEqualTo(ReviewConfirmation.Choice.CANCEL);
	}

	@Test
	void parseKeyIgnoresForeignIds() {
		assertThat(ReviewConfirmation.parseKey("other_button")).isNull();
		assertThat(ReviewConfirmation.parseChoice("other_button")).isNull();
	}
}
