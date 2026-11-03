package dev.pandesal.resume_reviewer.discord;

import dev.pandesal.resume_reviewer.config.ReviewProperties;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Holds a pending {@code /resume-review review} confirmation in memory.
 *
 * <p>When a user submits a resume, the bot replies with an ephemeral
 * confirmation message and two buttons ("Continue" / "Cancel"). The file bytes
 * are kept here (keyed by a UUID) and a virtual thread blocks on a
 * {@link CompletableFuture} for up to
 * {@code review.confirmation-timeout-seconds}. Clicking **Continue** runs the
 * evaluation and caches the result; clicking **Cancel** (or timing out)
 * discards it.
 */
@Slf4j
@Component
public class ReviewConfirmation {

    public static final String BUTTON_PREFIX = "resume_review_";

    /**
     * The user's choice for how to handle the submitted resume.
     */
    public enum Choice {
        /**
         * Run the evaluation and cache the result.
         */
        CONTINUE,
        /**
         * Discard the submission without evaluating.
         */
        CANCEL
    }

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final ExecutorService reviewExecutor;
    private final Duration timeout;

    public ReviewConfirmation(ExecutorService reviewExecutor, ReviewProperties reviewProperties) {
        this.reviewExecutor = reviewExecutor;
        this.timeout = Duration.ofSeconds(reviewProperties.confirmationTimeoutSeconds());
    }

    /**
     * Registers a pending confirmation and starts the blocking wait.
     *
     * @param key       a unique key (used to build the button component ids)
     * @param hook      the interaction hook (used to edit the message on timeout)
     * @param fileBytes the uploaded resume bytes
     * @param fileName  the original file name
     * @param onChoice  invoked on the executor once the user chooses (null on timeout)
     */
    public void register(String key, InteractionHook hook, byte[] fileBytes, String fileName,
                         ChoiceConsumer onChoice) {
        CompletableFuture<Choice> future = new CompletableFuture<>();
        pending.put(key, new Pending(hook, fileBytes, fileName, future));
        reviewExecutor.submit(() -> {
            try {
                Choice choice = future.get(timeout.toSeconds(), TimeUnit.SECONDS);
                onChoice.accept(choice);
            } catch (TimeoutException e) {
                log.info("Review confirmation {} timed out after {}", key, timeout);
                markTimedOut(key);
                onChoice.accept(null);
            } catch (Exception e) {
                log.error("Review confirmation {} wait failed", key, e);
            } finally {
                pending.remove(key);
            }
        });
    }

    /**
     * Resolves a pending confirmation with the user's choice.
     *
     * @param key    the confirmation key
     * @param choice the selected choice
     * @return true if a pending confirmation was resolved, false if already
     * resolved or timed out
     */
    public boolean resolve(String key, Choice choice) {
        Pending p = pending.get(key);
        if (p == null) {
            return false;
        }
        return p.future().complete(choice);
    }

    /**
     * Edits the confirmation message to show it timed out and removes the
     * buttons. Called from the blocking wait when no button was made in time.
     *
     * @param key the confirmation key
     */
    public void markTimedOut(String key) {
        Pending p = pending.get(key);
        if (p == null) {
            return;
        }
        p.hook().editOriginal("**Confirmation timed out** — no action taken. Please run `/resume-review review` again.")
                .setComponents(List.of())
                .queue();
    }

    /**
     * Builds the two confirmation buttons for a given key.
     *
     * @param key the confirmation key
     * @return the "Continue" and "Cancel" buttons
     */
    public Button[] buttons(String key) {
        return new Button[]{
                Button.primary(BUTTON_PREFIX + "continue_" + key, "Continue"),
                Button.secondary(BUTTON_PREFIX + "cancel_" + key, "Cancel")
        };
    }

    /**
     * Parses a button component id into a {@link Choice}, or null if the id is
     * not a review-confirmation button.
     *
     * @param componentId the button's component id
     * @return the choice, or null
     */
    public static Choice parseChoice(String componentId) {
        if (componentId == null || !componentId.startsWith(BUTTON_PREFIX)) {
            return null;
        }
        if (componentId.contains("_continue_")) {
            return Choice.CONTINUE;
        }
        if (componentId.contains("_cancel_")) {
            return Choice.CANCEL;
        }
        return null;
    }

    /**
     * Extracts the confirmation key from a button component id.
     *
     * @param componentId the button's component id
     * @return the key, or null if not a review-confirmation button
     */
    public static String parseKey(String componentId) {
        if (componentId == null || !componentId.startsWith(BUTTON_PREFIX)) {
            return null;
        }
        int continueIdx = componentId.indexOf("_continue_");
        if (continueIdx >= 0) {
            return componentId.substring(continueIdx + "_continue_".length());
        }
        int cancelIdx = componentId.indexOf("_cancel_");
        if (cancelIdx >= 0) {
            return componentId.substring(cancelIdx + "_cancel_".length());
        }
        return null;
    }

    /**
     * Callback receiving the user's choice (null on timeout).
     */
    @FunctionalInterface
    public interface ChoiceConsumer {
        void accept(Choice choice);
    }

    private record Pending(InteractionHook hook, byte[] fileBytes, String fileName, CompletableFuture<Choice> future) {
    }
}
