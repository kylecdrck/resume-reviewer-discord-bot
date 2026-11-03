package dev.pandesal.resume_reviewer.discord;

import dev.pandesal.resume_reviewer.config.BotConfigService;
import dev.pandesal.resume_reviewer.config.BotProperties;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Holds a pending {@code /resume-review config} form in memory.
 *
 * <p>When an admin runs the config command, the bot replies with an ephemeral
 * form: a multi-select channel menu (text/news/forum, up to 25 options) and
 * two buttons (Submit / Cancel). The selection is kept here (keyed by a UUID)
 * and a virtual thread blocks on a {@link CompletableFuture} for up to
 * {@code bot.config-form-timeout-seconds}. **Submit** saves the selected
 * channel IDs via {@link BotConfigService}; **Cancel** (or timeout) discards
 * the form without saving.
 */
@Slf4j
@Component
public class ConfigInteractionHandler {

    public static final String BUTTON_PREFIX = "resume_config_";
    public static final String MENU_PREFIX = "resume_config_channels_";

    /**
     * The admin's action on the config form.
     */
    public enum Action {
        /**
         * Save the selected channels.
         */
        SUBMIT,
        /**
         * Discard the form without saving.
         */
        CANCEL
    }

    private final BotConfigService botConfigService;
    private final ExecutorService reviewExecutor;
    private final Duration timeout;

    private final Map<String, PendingConfig> pending = new ConcurrentHashMap<>();

    public ConfigInteractionHandler(BotConfigService botConfigService, BotProperties properties,
                                    ExecutorService reviewExecutor) {
        this.botConfigService = botConfigService;
        this.reviewExecutor = reviewExecutor;
        this.timeout = Duration.ofSeconds(properties.configFormTimeoutSeconds());
    }

    /**
     * Replies with the ephemeral config form and starts the blocking wait.
     *
     * @param event the {@code /resume-review config} interaction
     */
    public void openForm(SlashCommandInteractionEvent event) {
        String key = UUID.randomUUID().toString();
        event.reply(formText())
                .setComponents(
                        ActionRow.of(EntitySelectMenu.create(MENU_PREFIX + key, SelectTarget.CHANNEL)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS, ChannelType.FORUM)
                                .setMinValues(0)
                                .setMaxValues(25)
                                .setPlaceholder("Select channels/forums (0-25)")
                                .build()),
                        ActionRow.of(
                                Button.primary(BUTTON_PREFIX + "submit_" + key, "Submit"),
                                Button.secondary(BUTTON_PREFIX + "cancel_" + key, "Cancel"))
                )
                .setEphemeral(true)
                .queue(hook -> {
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    pending.put(key, new PendingConfig(hook, new CopyOnWriteArrayList<>(), future));
                    reviewExecutor.submit(() -> {
                        try {
                            future.get(timeout.toSeconds(), TimeUnit.SECONDS);
                        } catch (TimeoutException e) {
                            log.info("Config form {} timed out after {}", key, timeout);
                            markTimedOut(key);
                        } catch (Exception e) {
                            log.error("Config form {} wait failed", key, e);
                        }
                    });
                });
    }

    /**
     * Handles the channel select menu: records the current selection.
     *
     * @param event the select menu interaction
     * @return true if the event was a config-form menu, false otherwise
     */
    public boolean handleSelectMenu(EntitySelectInteractionEvent event) {
        String key = parseKey(event.getComponentId());
        if (key == null) {
            return false;
        }
        event.deferEdit().queue();
        PendingConfig p = pending.get(key);
        if (p == null) {
            event.getHook().editOriginal("This configuration has already been handled or timed out.")
                    .setComponents(List.of())
                    .queue();
            return true;
        }
        p.selectedChannelIds().clear();
        for (var value : event.getValues()) {
            p.selectedChannelIds().add(value.getId());
        }
        return true;
    }

    /**
     * Handles the Submit / Cancel buttons.
     *
     * @param event the button interaction
     * @return true if the event was a config-form button, false otherwise
     */
    public boolean handleButton(ButtonInteractionEvent event) {
        Action action = parseAction(event.getComponentId());
        if (action == null) {
            return false;
        }
        String key = parseKey(event.getComponentId());
        event.deferEdit().queue();
        PendingConfig p = pending.remove(key);
        if (p == null) {
            event.getHook().editOriginal("This configuration has already been handled or timed out.")
                    .setComponents(List.of())
                    .queue();
            return true;
        }
        p.future().complete(null);
        if (action == Action.SUBMIT) {
            List<String> selected = List.copyOf(p.selectedChannelIds());
            botConfigService.saveAllowedChannels(selected);
            String message = selected.isEmpty()
                    ? "**Configuration saved** — no channels selected; the bot works in **all** channels."
                    : "**Configuration saved** — " + selected.size() + " channel(s) allowed.";
            event.getHook().editOriginal(message).setComponents(List.of()).queue();
        } else {
            event.getHook().editOriginal("**Configuration cancelled** — no changes saved.")
                    .setComponents(List.of())
                    .queue();
        }
        return true;
    }

    /**
     * Edits the form message to show it timed out and removes the components.
     *
     * @param key the form key
     */
    private void markTimedOut(String key) {
        PendingConfig p = pending.remove(key);
        if (p == null) {
            return;
        }
        p.hook().editOriginal("**Configuration timed out** — no changes saved. Please run `/resume-review config` again.")
                .setComponents(List.of())
                .queue();
    }

    /**
     * @return the ephemeral form text
     */
    private String formText() {
        return "**Configure allowed channels**\n"
                + "Select one or more channels/forums where the bot's features can be used.\n"
                + "Leave the selection empty to allow the bot in **all** channels.\n\n"
                + "This form times out after " + timeout.toMinutes() + " minutes.";
    }

    /**
     * Parses a button component id into an {@link Action}, or null if the id
     * is not a config-form button.
     *
     * @param componentId the button's component id
     * @return the action, or null
     */
    public static Action parseAction(String componentId) {
        if (componentId == null || !componentId.startsWith(BUTTON_PREFIX)) {
            return null;
        }
        if (componentId.contains("_submit_")) {
            return Action.SUBMIT;
        }
        if (componentId.contains("_cancel_")) {
            return Action.CANCEL;
        }
        return null;
    }

    /**
     * Extracts the form key from a component id (button or select menu), or
     * null if the id is not a config-form component.
     *
     * @param componentId the component id
     * @return the key, or null
     */
    public static String parseKey(String componentId) {
        if (componentId == null) {
            return null;
        }
        if (componentId.startsWith(MENU_PREFIX)) {
            return componentId.substring(MENU_PREFIX.length());
        }
        if (!componentId.startsWith(BUTTON_PREFIX)) {
            return null;
        }
        int submitIdx = componentId.indexOf("_submit_");
        if (submitIdx >= 0) {
            return componentId.substring(submitIdx + "_submit_".length());
        }
        int cancelIdx = componentId.indexOf("_cancel_");
        if (cancelIdx >= 0) {
            return componentId.substring(cancelIdx + "_cancel_".length());
        }
        return null;
    }

    /**
     * @param componentId the select menu's component id
     * @return true if the id is a config-form channel menu
     */
    public static boolean isConfigSelect(String componentId) {
        return componentId != null && componentId.startsWith(MENU_PREFIX);
    }

    private record PendingConfig(InteractionHook hook, List<String> selectedChannelIds,
                                 CompletableFuture<Void> future) {
    }
}
