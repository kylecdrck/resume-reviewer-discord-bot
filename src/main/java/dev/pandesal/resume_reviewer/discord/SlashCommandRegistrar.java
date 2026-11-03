package dev.pandesal.resume_reviewer.discord;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registers the bot's slash commands with Discord when it becomes ready.
 * Uses global commands (requires the application's client ID to be set);
 * global commands can take up to an hour to propagate.
 */
@Slf4j
@Component
public class SlashCommandRegistrar extends ListenerAdapter {

    private final List<Command> commands;

    public SlashCommandRegistrar(List<Command> commands) {
        this.commands = commands;
    }

    @Override
    public void onReady(ReadyEvent event) {
        JDA jda = event.getJDA();
        List<CommandData> data = commands.stream().map(Command::command).toList();
        log.info("Registering {} global slash command(s): {}", data.size(),
                data.stream().map(CommandData::getName).toList());
        jda.updateCommands().addCommands(data).queue(
                registered -> log.info("Registered slash commands: {}", registered),
                error -> log.error("Failed to register slash commands", error)
        );
    }
}
