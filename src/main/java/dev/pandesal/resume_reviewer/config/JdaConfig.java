package dev.pandesal.resume_reviewer.config;

import dev.pandesal.resume_reviewer.discord.Command;
import dev.pandesal.resume_reviewer.discord.ConfigInteractionHandler;
import dev.pandesal.resume_reviewer.discord.JdaListener;
import dev.pandesal.resume_reviewer.discord.ReviewConfirmation;
import dev.pandesal.resume_reviewer.discord.ResumeReviewCommand;
import dev.pandesal.resume_reviewer.discord.SlashCommandRegistrar;
import dev.pandesal.resume_reviewer.review.ResumeReviewService;
import dev.pandesal.resume_reviewer.review.RoleSuitabilityEvaluator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the JDA bot, registers the interaction listener, and provides a
 * virtual-thread executor used to offload long-running review work so the
 * JDA event loop stays responsive.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({DiscordProperties.class, LlmProperties.class, ReviewProperties.class, BotProperties.class})
public class JdaConfig {

	/** Maximum number of reviews processed concurrently. */
	private static final int MAX_CONCURRENT_REVIEWS = 4;

	@Bean
	@ConditionalOnProperty(prefix = "discord", name = "enabled", havingValue = "true")
	public JDA jda(DiscordProperties props, JdaListener listener, SlashCommandRegistrar registrar) {
		log.info("Starting Discord bot...");
		JDA jda = JDABuilder.createDefault(props.token())
			.enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
			.addEventListeners(listener, registrar)
			.build();
		Runtime.getRuntime().addShutdownHook(new Thread(jda::shutdownNow, "jda-shutdown"));
		return jda;
	}

	@Bean
	public JdaListener jdaListener(List<Command> commands, ReviewConfirmation reviewConfirmation,
			ConfigInteractionHandler configInteractionHandler, ResumeReviewCommand resumeReviewCommand) {
			return new JdaListener(commands, reviewConfirmation, configInteractionHandler, resumeReviewCommand);
	}

	@Bean
	public ResumeReviewCommand resumeReviewCommand(ResumeReviewService service,
			RoleSuitabilityEvaluator roleSuitabilityEvaluator, ReviewConfirmation reviewConfirmation,
				ConfigInteractionHandler configInteractionHandler, BotConfigService botConfigService,
				ExecutorService reviewExecutor) {
				return new ResumeReviewCommand(service, roleSuitabilityEvaluator, reviewConfirmation,
					configInteractionHandler, botConfigService, reviewExecutor);
			}

	/**
		 * Bounded executor for review work. Virtual threads keep the JDA event loop
		 * free, while the semaphore caps how many PDFs are parsed and sent to the
		 * LLM at once so a burst of uploads cannot exhaust memory or the LLM quota.
		 */
		@Bean
		public ExecutorService reviewExecutor() {
			Semaphore permits = new Semaphore(MAX_CONCURRENT_REVIEWS);
			return new ThreadPoolExecutor(MAX_CONCURRENT_REVIEWS, MAX_CONCURRENT_REVIEWS, 0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<>(), Thread.ofVirtual().name("review-", 0).factory());
		}
	}
