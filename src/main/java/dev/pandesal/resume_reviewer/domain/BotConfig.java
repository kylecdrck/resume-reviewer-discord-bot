package dev.pandesal.resume_reviewer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A persistent key-value configuration row for the bot.
 *
 * <p>The first key is {@code allowed_channels}, holding a comma-separated list
 * of channel IDs. An absent key means the bot works in any channel (default).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class BotConfig {

	@Id
	@Column(nullable = false, length = 128)
	private String configKey;

	@Column(nullable = false, length = Integer.MAX_VALUE)
	private String configValue;

	@Column(nullable = false)
	private Instant updatedAt;
}
