package dev.pandesal.resume_reviewer.discord;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes incoming slash commands to their {@link Command} handlers, component
 * interactions (buttons, select menus) to their handlers, and new forum posts
 * to the auto-review trigger.
 */
@Slf4j
public class JdaListener extends ListenerAdapter {

    private final Map<String, Command> commands;
    private final ReviewConfirmation reviewConfirmation;
    private final ConfigInteractionHandler configInteractionHandler;
    private final ResumeReviewCommand resumeReviewCommand;

    public JdaListener(List<Command> commands, ReviewConfirmation reviewConfirmation,
                       ConfigInteractionHandler configInteractionHandler, ResumeReviewCommand resumeReviewCommand) {
        this.commands = commands.stream()
                .collect(Collectors.toMap(Command::name, Function.identity()));
        this.reviewConfirmation = reviewConfirmation;
        this.configInteractionHandler = configInteractionHandler;
        this.resumeReviewCommand = resumeReviewCommand;
    }

    @Override
    public void onChannelCreate(ChannelCreateEvent event) {
        if (event.getChannel() instanceof ThreadChannel thread) {
            resumeReviewCommand.handleAutoReview(thread);
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Command command = commands.get(event.getName());
        if (command == null) {
            log.warn("No handler for slash command {}", event.getName());
            event.reply("Unknown command.").setEphemeral(true).queue();
            return;
        }
        command.handle(event);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (configInteractionHandler.handleButton(event)) {
            return;
        }
        ReviewConfirmation.Choice choice = ReviewConfirmation.parseChoice(event.getComponentId());
        if (choice == null) {
            return; // not one of our buttons
        }
        String key = ReviewConfirmation.parseKey(event.getComponentId());
        event.deferEdit().queue();
        if (!reviewConfirmation.resolve(key, choice)) {
            // Already resolved or timed out.
            event.getHook().editOriginal("This confirmation has already been handled or timed out.")
                    .setComponents(List.of())
                    .queue();
        } else if (choice == ReviewConfirmation.Choice.CANCEL) {
            event.getHook().editOriginal("**Review cancelled** — no evaluation was run.")
                    .setComponents(List.of())
                    .queue();
        } else {
            // Acknowledge the choice; the review will post its result in the thread.
            event.getHook().editOriginal("**Evaluating your resume…**\n"
                            + "The result will be posted in this thread shortly.")
                    .setComponents(List.of())
                    .queue();
        }
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (ConfigInteractionHandler.isConfigSelect(event.getComponentId())) {
            configInteractionHandler.handleSelectMenu(event);
        }
    }
}
