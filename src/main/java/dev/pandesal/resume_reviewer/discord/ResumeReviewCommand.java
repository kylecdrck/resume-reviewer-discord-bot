package dev.pandesal.resume_reviewer.discord;

import dev.pandesal.resume_reviewer.config.BotConfigService;
import dev.pandesal.resume_reviewer.pdf.PdfTextExtractor;
import dev.pandesal.resume_reviewer.review.ResumeReviewService;
import dev.pandesal.resume_reviewer.review.ReviewOutcome;
import dev.pandesal.resume_reviewer.review.RoleSuitabilityEvaluator;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code /resume-review review <file> [role_suitability]} — evaluates a resume PDF via the LLM
 * and replies with the full report.
 *
 * <p>Only usable in forum threads, and only by the thread author. Replies with
 * an ephemeral confirmation (two buttons: Continue / Cancel). **Continue**
 * runs the evaluation in the background, caches the result (keyed by the
 * original file hash, with the stored text redacted of PII), and posts the
 * report in the thread. **Cancel** (or timeout) discards the submission.
 *
 * <p>When the optional comma-separated {@code role_suitability} option is
 * given and the resume was not already cached, a separate LLM call measures
 * the resume against each role and posts the result after the ATS report.
 *
 * <p>{@code /resume-review config} — admin-only. Opens an ephemeral form to
 * select the channels/forums where the bot's features are allowed.
 *
 * <p><b>Auto-review.</b> When a post is created in an allowed forum and its
 * starter message carries exactly one PDF attachment whose name starts with
 * {@value #AUTO_REVIEW_PREFIX}, the review runs without a slash command and
 * without the confirmation gate. Only the starter message is inspected;
 * replies inside the thread are ignored. An optional line
 * {@code REVIEW_ROLES: role1,role2} in the starter message (exact case, first
 * match only, max {@value #MAX_ROLES} roles) additionally runs the role
 * suitability analysis.
 */
@Slf4j
public class ResumeReviewCommand extends BaseFileCommand {

    /**
     * Attachment name prefix that triggers an automatic review.
     */
    public static final String AUTO_REVIEW_PREFIX = "FOR_REVIEW_";

    /**
     * Line prefix that requests role suitability in an auto-review post.
     */
    public static final String REVIEW_ROLES_PREFIX = "REVIEW_ROLES:";

    private static final Pattern REVIEW_ROLES_LINE =
            Pattern.compile("^" + REVIEW_ROLES_PREFIX + "\\s*(.*)$", Pattern.MULTILINE);

    private static final String SUBCOMMAND_REVIEW = "review";
    private static final String SUBCOMMAND_CONFIG = "config";
    private static final String OPTION_ROLE_SUITABILITY = "role_suitability";
    private static final int MAX_ROLES = 5;

    private final ResumeReviewService service;
    private final RoleSuitabilityEvaluator roleSuitabilityEvaluator;
    private final ReviewConfirmation reviewConfirmation;
    private final ConfigInteractionHandler configInteractionHandler;
    private final BotConfigService botConfigService;

    public ResumeReviewCommand(ResumeReviewService service, RoleSuitabilityEvaluator roleSuitabilityEvaluator,
                               ReviewConfirmation reviewConfirmation, ConfigInteractionHandler configInteractionHandler,
                               BotConfigService botConfigService, ExecutorService executor) {
        super(executor);
        this.service = service;
        this.roleSuitabilityEvaluator = roleSuitabilityEvaluator;
        this.reviewConfirmation = reviewConfirmation;
        this.configInteractionHandler = configInteractionHandler;
        this.botConfigService = botConfigService;
    }

    @Override
    public CommandData command() {
        return Commands.slash("resume-review", "Review a resume PDF via the LLM")
                .setContexts(InteractionContextType.GUILD)
                .setIntegrationTypes(IntegrationType.GUILD_INSTALL)
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED)
                .addSubcommands(
                        new SubcommandData(SUBCOMMAND_REVIEW, "Review a resume PDF via the LLM")
                                .addOptions(
                                        new OptionData(OptionType.ATTACHMENT, "file", "The resume file (PDF)", true),
                                        new OptionData(OptionType.STRING, OPTION_ROLE_SUITABILITY,
                                                "Optional comma-separated roles (max 5), e.g. \"software engineer,QA,tester\"",
                                                false)),
                        new SubcommandData(SUBCOMMAND_CONFIG, "Restrict bot features to selected channels/forums (admin)")
                );
    }

    @Override
    protected void handleSubcommand(SlashCommandInteractionEvent event, String subcommand) {
        switch (subcommand) {
            case SUBCOMMAND_REVIEW -> handleReview(event);
            case SUBCOMMAND_CONFIG -> handleConfig(event);
            default -> super.handleSubcommand(event, subcommand);
        }
    }

    /**
     * Handles {@code /resume-review config}.
     *
     * <p>Requires a guild and the ADMINISTRATOR permission, then opens the
     * ephemeral channel-selection form.
     */
    private void handleConfig(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("This command can only be used in a **server**.").setEphemeral(true).queue();
            return;
        }
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("Server **administrator** permission is required.").setEphemeral(true).queue();
            return;
        }
        configInteractionHandler.openForm(event);
    }

    /**
     * Handles a newly created forum post.
     *
     * <p>Only the starter message is inspected, so replies inside the thread
     * never re-trigger. The review runs when the post's parent forum is on the
     * allow-list and the starter message carries exactly one PDF attachment
     * named {@code FOR_REVIEW_*.pdf} of at most {@link #MAX_FILE_SIZE_BYTES}.
     * An optional {@code REVIEW_ROLES:} line in the starter message requests
     * the role suitability analysis.
     *
     * <p>The starter message is always authored by the thread owner, so no
     * author check is needed; only bot-authored posts are skipped.
     *
     * @param thread the newly created post thread
     */
    public void handleAutoReview(ThreadChannel thread) {
        if (!(thread.getParentChannel() instanceof ForumChannel forum)) {
            return;
        }
        if (!botConfigService.isChannelAllowed(forum.getId())) {
            return;
        }
        thread.retrieveStartMessage().queue(starter -> {
            if (starter.getAuthor().isBot()) {
                return;
            }
            handleStarterMessage(thread, starter);
        }, error -> log.warn("Could not retrieve the starter message of post {}", thread.getId(), error));
    }

    private void handleStarterMessage(ThreadChannel thread, Message starter) {
        List<Message.Attachment> attachments = starter.getAttachments().stream()
                .filter(attachment -> isAutoReviewFile(attachment.getFileName()))
                .toList();
        if (attachments.isEmpty()) {
            return;
        }
        if (attachments.size() > 1) {
            post(thread, "**Auto-review skipped** — attach exactly one `" + AUTO_REVIEW_PREFIX + "*.pdf` file.");
            return;
        }
        Message.Attachment attachment = attachments.getFirst();
        if (attachment.getSize() > MAX_FILE_SIZE_BYTES) {
            post(thread, "**Auto-review skipped** — file is too large. Maximum size is **5MB**.");
            return;
        }

        String fileName = attachment.getFileName();
        List<String> roles = parseReviewRoles(starter.getContentRaw());
        String key = UUID.randomUUID().toString();
        log.info("Auto-review {} triggered: file={}, roles={}", key, fileName, roles);

        try (var executor = executor()) {
            executor.submit(() -> {
                File temp = null;
                try {
                    byte[] fileBytes = attachment.getProxy().download().join().readAllBytes();
                    temp = File.createTempFile("resume-", ".pdf");
                    Files.write(temp.toPath(), fileBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    if (!PdfTextExtractor.hasPdfMagic(temp)) {
                        post(thread, "**Error:** That file is not a valid PDF.");
                        return;
                    }
                    runReview(key, thread, fileBytes, fileName, roles);
                } catch (Exception e) {
                    log.error("Auto-review {} failed", key, e);
                    post(thread, "**Error:** could not download the file.");
                } finally {
                    if (temp != null) {
                        try {
                            Files.deleteIfExists(temp.toPath());
                        } catch (IOException ignored) {
                            // best-effort cleanup
                        }
                    }
                }
            });
        }
    }

    /**
     * @param fileName the attachment file name (may be null)
     * @return true if the name is a PDF and starts with {@value #AUTO_REVIEW_PREFIX}
     */
    static boolean isAutoReviewFile(String fileName) {
        return fileName != null && fileName.startsWith(AUTO_REVIEW_PREFIX) && isSupportedResumeFile(fileName);
    }

    /**
     * Extracts the roles requested by a {@code REVIEW_ROLES: a,b,c} line in the
     * post body. The marker must start a line and match exactly; only the first
     * matching line is used and the result is capped at {@link #MAX_ROLES}.
     *
     * @param content the raw message content (may be null)
     * @return the parsed roles, empty when the marker is absent
     */
    static List<String> parseReviewRoles(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        Matcher matcher = REVIEW_ROLES_LINE.matcher(content);
        if (!matcher.find()) {
            return List.of();
        }
        List<String> roles = parseRoles(matcher.group(1));
        return List.copyOf(roles.subList(0, Math.min(roles.size(), MAX_ROLES)));
    }

    private static void post(ThreadChannel channel, String text) {
        channel.sendMessage(text).setAllowedMentions(EnumSet.noneOf(MentionType.class)).queue();
    }

    /**
     * Handles {@code /resume-review review <file>}.
     *
     * <p>Validates the forum-thread + thread-owner constraints, downloads the
     * attachment into memory, replies with an ephemeral confirmation message
     * and two buttons, then blocks (on a virtual thread) for the user's choice
     * up to the configured timeout. On **Continue**, the review runs in the
     * background and the result is posted in the thread.
     */
    private void handleReview(SlashCommandInteractionEvent event) {
        // Must be a forum thread.
        if (event.getChannelType() != ChannelType.GUILD_PUBLIC_THREAD
                && event.getChannelType() != ChannelType.GUILD_NEWS_THREAD
                && event.getChannelType() != ChannelType.GUILD_PRIVATE_THREAD) {
            event.reply("This command can only be used in a **forum thread**.").setEphemeral(true).queue();
            return;
        }
        ThreadChannel thread = (ThreadChannel) event.getChannel();
        // The thread's parent forum must be on the allow list (when one is configured).
        if (!botConfigService.isChannelAllowed(thread.getParentChannel().getId())) {
            event.reply("This command cannot be used here").setEphemeral(true).queue();
            return;
        }
        // Must be the thread author.
        if (!thread.getOwnerId().equals(event.getUser().getId())) {
            event.reply("This command can only be used by the author of the thread.").setEphemeral(true).queue();
            return;
        }

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

        OptionMapping roleOption = event.getOption(OPTION_ROLE_SUITABILITY);
        List<String> roles = parseRoles(roleOption == null ? null : roleOption.getAsString());
        if (roles.size() > MAX_ROLES) {
            event.reply("Please provide at most **" + MAX_ROLES + "** roles in `role_suitability`.").setEphemeral(true).queue();
            return;
        }

        // Download the attachment into memory so the button press doesn't need to re-fetch it.
        byte[] fileBytes;
        try {
            fileBytes = attachment.getProxy().download().join().readAllBytes();
        } catch (Exception e) {
            log.error("Failed to download attachment {}", fileName, e);
            event.reply("**Error:** could not download the file.").setEphemeral(true).queue();
            return;
        }

        ThreadChannel channel = thread;
        String key = UUID.randomUUID().toString();

        // Reply with the ephemeral confirmation + buttons, then start the blocking wait.
        event.reply(confirmationText(roles))
                .setComponents(ActionRow.of(reviewConfirmation.buttons(key)))
                .setEphemeral(true)
                .queue(hook -> reviewConfirmation.register(key, hook, fileBytes, fileName,
                        choice -> runReview(key, channel, fileBytes, fileName, roles, choice)));
    }

    /**
     * Runs the review in the background after the user's choice (or timeout).
     *
     * <p>When roles were requested and the resume was freshly evaluated (not
     * served from the cache), a separate role suitability LLM call runs after
     * the ATS report is posted and its result is posted as a separate message.
     *
     * @param key       the confirmation key
     * @param channel   the thread to post the result in
     * @param fileBytes the uploaded resume bytes
     * @param fileName  the original file name
     * @param roles     the requested roles (empty when not requested)
     * @param choice    the user's choice, or null if the confirmation timed out
     */
    private void runReview(String key, ThreadChannel channel, byte[] fileBytes, String fileName,
                           List<String> roles, ReviewConfirmation.Choice choice) {
        if (choice != ReviewConfirmation.Choice.CONTINUE) {
            // Cancelled or timed out — the confirmation message was already updated.
            log.info("Review {} not run: choice={}", key, choice);
            return;
        }
        log.info("Review {} confirmed: file={}, roles={}", key, fileName, roles);
        runReview(key, channel, fileBytes, fileName, roles);
    }

    /**
     * Runs the review and posts the result. Must be called on the review
     * executor.
     *
     * @param key       the correlation key
     * @param channel   the thread to post the result in
     * @param fileBytes the uploaded resume bytes
     * @param fileName  the original file name
     * @param roles     the requested roles (empty when not requested)
     */
    void runReview(String key, ThreadChannel channel, byte[] fileBytes, String fileName,
                   List<String> roles) {
        File temp = null;
        try {
            temp = File.createTempFile("resume-", ".pdf");
            Files.write(temp.toPath(), fileBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            if (!PdfTextExtractor.hasPdfMagic(temp)) {
                post(channel, "**Error:** That file is not a valid PDF.");
                return;
            }
            ReviewOutcome outcome = service.review(temp, true, roles);
            List<String> chunks = DiscordMessages.review(outcome.result());
            sendChunks(channel, chunks);
            log.info("Review {} result posted ({} chunk(s))", key, chunks.size());
            if (!roles.isEmpty()) {
                if (outcome.fresh()) {
                    runRoleSuitability(channel, temp, roles);
                } else {
                    log.info("Review {} served from cache; skipping role suitability", key);
                    post(channel, "**Role Suitability**\nSkipped: this resume was already reviewed.");
                }
            }
        } catch (Exception e) {
            log.error("Review {} failed", key, e);
            post(channel, "**Error:** " + e.getMessage());
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp.toPath());
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    /**
     * Runs the role suitability analysis and posts it. Failures are non-fatal:
     * the ATS report has already been posted.
     */
    private void runRoleSuitability(ThreadChannel channel, File resume, List<String> roles) {
        try {
            var result = roleSuitabilityEvaluator.evaluate(service.extractText(resume), roles);
            sendChunks(channel, DiscordMessages.roleSuitability(result));
        } catch (Exception e) {
            log.error("Role suitability analysis failed", e);
            post(channel, "**Role Suitability**\n**Error:** " + e.getMessage());
        }
    }

    /**
     * Parses the comma-separated {@code role_suitability} option: trims each
     * role, drops blanks, and de-duplicates case-insensitively (first
     * occurrence wins).
     *
     * @param raw the raw option value (may be null)
     * @return the parsed roles, in input order
     */
    static List<String> parseRoles(String raw) {
        Map<String, String> unique = new LinkedHashMap<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                String role = part.trim();
                if (!role.isEmpty()) {
                    unique.putIfAbsent(role.toLowerCase(Locale.ROOT), role);
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * @param roles the requested roles (empty when not requested)
     * @return the ephemeral confirmation text shown before the review runs
     */
    private String confirmationText(List<String> roles) {
        StringBuilder text = new StringBuilder("**Resume review**\n")
                .append("Your resume will be evaluated by the LLM.\n\n");
        if (!roles.isEmpty()) {
            text.append("Role suitability will also be measured for: **")
                    .append(String.join(", ", roles))
                    .append("**.\n\n");
        }
        return text.append("Would you like to continue?").toString();
    }

    /**
     * Sends the review result chunks to the channel.
     *
     * @param channel the target channel
     * @param chunks  the message chunks
     */
    private void sendChunks(ThreadChannel channel, List<String> chunks) {
        for (String chunk : chunks) {
            channel.sendMessage(chunk).setAllowedMentions(EnumSet.noneOf(MentionType.class)).queue();
        }
    }

    @Override
    protected List<String> run(File file) throws Exception {
        return DiscordMessages.review(service.review(file, true));
    }
}
