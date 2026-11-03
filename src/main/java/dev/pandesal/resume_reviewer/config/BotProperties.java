package dev.pandesal.resume_reviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bot behavior configuration.
 *
 * @param configFormTimeoutSeconds how long (seconds) the {@code /resume-review config} form stays open before timing out
 * @param configCacheTtlSeconds    how long (seconds) the in-memory config cache is valid before being renewed from the DB
 */
@ConfigurationProperties(prefix = "bot")
public record BotProperties(int configFormTimeoutSeconds, int configCacheTtlSeconds) {
}
