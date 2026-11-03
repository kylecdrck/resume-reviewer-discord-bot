package dev.pandesal.resume_reviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Discord bot credentials.
 *
 * @param enabled whether to start the Discord bot (set false in tests)
 * @param token   the bot token (required when enabled)
 */
@ConfigurationProperties(prefix = "discord")
public record DiscordProperties(boolean enabled, String token) {
}
