package dev.pandesal.resume_reviewer.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

/**
 * A Discord slash command the bot can register and dispatch.
 */
public interface Command {

    /**
     * @return the command definition to register with Discord
     */
    CommandData command();

    /**
     * @return the command name (lowercase, no slash)
     */
    String name();

    /**
     * Handles the incoming slash command event.
     */
    void handle(SlashCommandInteractionEvent event);
}
