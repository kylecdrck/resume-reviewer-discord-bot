package dev.pandesal.resume_reviewer.discord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfigInteractionHandlerTest {

	@Test
	void parseActionSubmit() {
		String id = ConfigInteractionHandler.BUTTON_PREFIX + "submit_abc-123";
		assertThat(ConfigInteractionHandler.parseAction(id)).isEqualTo(ConfigInteractionHandler.Action.SUBMIT);
	}

	@Test
	void parseActionCancel() {
		String id = ConfigInteractionHandler.BUTTON_PREFIX + "cancel_abc-123";
		assertThat(ConfigInteractionHandler.parseAction(id)).isEqualTo(ConfigInteractionHandler.Action.CANCEL);
	}

	@Test
	void parseActionIgnoresForeignIds() {
		assertThat(ConfigInteractionHandler.parseAction("other_button")).isNull();
		assertThat(ConfigInteractionHandler.parseAction(null)).isNull();
	}

	@Test
	void parseKeyFromSubmitButton() {
		String id = ConfigInteractionHandler.BUTTON_PREFIX + "submit_abc-123";
		assertThat(ConfigInteractionHandler.parseKey(id)).isEqualTo("abc-123");
	}

	@Test
	void parseKeyFromCancelButton() {
		String id = ConfigInteractionHandler.BUTTON_PREFIX + "cancel_abc-123";
		assertThat(ConfigInteractionHandler.parseKey(id)).isEqualTo("abc-123");
	}

	@Test
	void parseKeyFromSelectMenu() {
		String id = ConfigInteractionHandler.MENU_PREFIX + "abc-123";
		assertThat(ConfigInteractionHandler.parseKey(id)).isEqualTo("abc-123");
		assertThat(ConfigInteractionHandler.isConfigSelect(id)).isTrue();
	}

	@Test
	void parseKeyIgnoresForeignIds() {
		assertThat(ConfigInteractionHandler.parseKey("other_button")).isNull();
		assertThat(ConfigInteractionHandler.parseKey(null)).isNull();
		assertThat(ConfigInteractionHandler.isConfigSelect("other_menu")).isFalse();
	}

	@Test
	void configIdsDoNotParseAsReviewConfirmation() {
		String submit = ConfigInteractionHandler.BUTTON_PREFIX + "submit_abc-123";
		String cancel = ConfigInteractionHandler.BUTTON_PREFIX + "cancel_abc-123";
		assertThat(ReviewConfirmation.parseChoice(submit)).isNull();
		assertThat(ReviewConfirmation.parseChoice(cancel)).isNull();
		assertThat(ReviewConfirmation.parseKey(submit)).isNull();
		assertThat(ReviewConfirmation.parseKey(cancel)).isNull();
	}
}
