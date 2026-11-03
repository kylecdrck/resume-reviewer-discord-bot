package dev.pandesal.resume_reviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Review configuration.
 *
 * @param confirmationTimeoutSeconds how long (seconds) to wait for the user to confirm a review before cancelling
 */
@ConfigurationProperties(prefix = "review")
public record ReviewProperties(int confirmationTimeoutSeconds) {
}
