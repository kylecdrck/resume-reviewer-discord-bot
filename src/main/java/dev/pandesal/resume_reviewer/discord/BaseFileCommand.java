package dev.pandesal.resume_reviewer.discord;

import dev.pandesal.resume_reviewer.pdf.PdfTextExtractor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Base class for file-attachment slash commands. Handles deferring the reply,
 * downloading the attachment to a temp file, offloading the work to the
 * virtual-thread executor, and replying in the same channel/thread.
 */
@Slf4j
public abstract class BaseFileCommand implements Command {

    /**
     * Maximum accepted attachment size: 5MB.
     */
    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private final ExecutorService executor;

    protected BaseFileCommand(ExecutorService executor) {
        this.executor = executor;
    }

    /**
     * @return the virtual-thread executor used to offload long-running work
     */
    protected ExecutorService executor() {
        return executor;
    }

    /**
     * @return the command definition to register with Discord
     */
    public abstract CommandData command();

    /**
     * Performs the work and returns the message chunks to send.
     *
     * @param file the downloaded attachment
     * @return message chunks (each within Discord's limit)
     * @throws Exception on any processing error
     */
    protected abstract List<String> run(File file) throws Exception;

    /**
     * Entry point invoked by the listener for this command's name.
     * Routes to the subcommand handler when a subcommand was used,
     * otherwise to the file-attachment handler.
     */
    @Override
    public final void handle(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand != null) {
            handleSubcommand(event, subcommand);
            return;
        }
        handleFile(event);
    }

    /**
     * Handles a subcommand invocation. Default: unknown subcommand.
     */
    protected void handleSubcommand(SlashCommandInteractionEvent event, String subcommand) {
        event.reply("Unknown subcommand: " + subcommand).setEphemeral(true).queue();
    }

    /**
     * Handles the file-attachment path of this command.
     */
    protected void handleFile(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption("file");
        if (option == null || option.getAsAttachment() == null) {
            event.reply("Please attach a file using the `file` option (PDF).").setEphemeral(true).queue();
            return;
        }
        var attachment = option.getAsAttachment();
        String fileName = attachment.getFileName();
        if (fileName == null || !isSupportedResumeFile(fileName)) {
            event.reply("Please attach a **PDF** file.").setEphemeral(true).queue();
            return;
        }
        if (attachment.getSize() > MAX_FILE_SIZE_BYTES) {
            event.reply("File is too large. Maximum size is **5MB**.").setEphemeral(true).queue();
            return;
        }

        // Capture the channel up front: the deferred hook is a webhook and has no channel of its own.
        MessageChannelUnion channel = event.getChannel();

        // Defer so we can take longer than Discord's 3s ack window, then reply in-thread.
        event.deferReply().queue(hook -> executor.submit(() -> {
            try (InputStream in = attachment.getProxy().download().join()) {
                // Always use a .pdf suffix: the name is attacker-controlled and may end in anything.
                File temp = File.createTempFile("resume-", ".pdf");
                Files.copy(in, temp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                try {
                    if (!PdfTextExtractor.hasPdfMagic(temp)) {
                        channel.sendMessage("**Error:** That file is not a valid PDF.").queue();
                        return;
                    }
                    List<String> chunks = run(temp);
                    sendChunks(channel, chunks);
                } finally {
                    Files.deleteIfExists(temp.toPath());
                }
            } catch (Exception e) {
                log.error("Command {} failed", name(), e);
                channel.sendMessage("**Error:** " + e.getMessage()).queue();
            }
        }));
    }

    /**
     * @return true if the file name has a supported resume extension (pdf)
     */
    public static boolean isSupportedResumeFile(String fileName) {
        return fileName.toLowerCase().endsWith(".pdf");
    }

    private void sendChunks(MessageChannelUnion channel, List<String> chunks) {
        for (String chunk : chunks) {
            channel.sendMessage(chunk).setAllowedMentions(EnumSet.noneOf(MentionType.class)).queue();
        }
    }

    /**
     * @return the command name (lowercase, no slash)
     */
    @Override
    public String name() {
        return command().getName();
    }

    /**
     * Helper to build a slash command with a required resume-file attachment
     * option (PDF).
     */
    protected SlashCommandData fileCommand(String name, String description) {
        return Commands.slash(name, description)
                .addOptions(new OptionData(OptionType.ATTACHMENT, "file", "The resume file (PDF)", true));
    }
}
