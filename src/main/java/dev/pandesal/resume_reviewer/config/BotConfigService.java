package dev.pandesal.resume_reviewer.config;

import dev.pandesal.resume_reviewer.domain.BotConfig;
import dev.pandesal.resume_reviewer.repository.BotConfigRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes persistent bot configuration (key-value rows in
 * {@code bot_config}).
 *
 * <p>Values are served from a small in-memory cache that is renewed lazily:
 * the DB is only queried when the cache entry is older than
 * {@code bot.config-cache-ttl-seconds}, then renewed on the next call. Saves
 * refresh the cache immediately (write-through) so the admin's own changes
 * are visible without waiting for the TTL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotConfigService {

	/** Config key holding the comma-separated list of allowed channel IDs. */
	public static final String KEY_ALLOWED_CHANNELS = "allowed_channels";

	private final BotConfigRepository repository;
	private final BotProperties properties;

	private final AtomicReference<CacheEntry> cache = new AtomicReference<>();

	/**
	 * @return the currently configured allowed channel IDs, or empty when the
	 *         bot is unconfigured (works in any channel)
	 */
	public Optional<List<String>> allowedChannels() {
		Instant now = Instant.now();
		CacheEntry entry = cache.get();
		if (entry != null && isFresh(entry, now)) {
			return entry.value();
		}
		synchronized (this) {
			entry = cache.get();
			now = Instant.now();
			if (entry != null && isFresh(entry, now)) {
				return entry.value();
			}
			Optional<List<String>> value = loadFromDb();
			cache.set(new CacheEntry(Instant.now(), value));
			return value;
		}
	}

	/**
	 * @param channelId the channel ID to check
	 	 * @return true when no channels are configured (any channel allowed) or the
	 *         channel is in the configured list
	 */
	public boolean isChannelAllowed(String channelId) {
	 		return allowedChannels().filter(ids -> !ids.isEmpty()).map(ids -> ids.contains(channelId)).orElse(true);
	}

	/**
	 * Persists the allowed channel IDs. An empty list deletes the row,
	 * restoring the default (bot works in any channel).
	 *
	 * @param channelIds the channel IDs to allow (may be empty)
	 */
	@Transactional
	public void saveAllowedChannels(List<String> channelIds) {
		List<String> normalized = normalize(channelIds);
		if (normalized.isEmpty()) {
			repository.findById(KEY_ALLOWED_CHANNELS).ifPresent(repository::delete);
			log.info("Allowed channels cleared; bot works in any channel");
		}
		else {
			BotConfig config = repository.findById(KEY_ALLOWED_CHANNELS).orElseGet(BotConfig::new);
			config.setConfigKey(KEY_ALLOWED_CHANNELS);
			config.setConfigValue(String.join(",", normalized));
			config.setUpdatedAt(Instant.now());
			repository.save(config);
			log.info("Saved {} allowed channel(s)", normalized.size());
		}
		// Write-through: refresh the cache so this change is visible immediately.
		cache.set(new CacheEntry(Instant.now(), Optional.of(normalized)));
	}

	private boolean isFresh(CacheEntry entry, Instant now) {
		return Duration.between(entry.fetchedAt(), now).getSeconds() < properties.configCacheTtlSeconds();
	}

	private Optional<List<String>> loadFromDb() {
		return repository.findById(KEY_ALLOWED_CHANNELS)
			.map(config -> normalize(List.of(config.getConfigValue().split(","))));
	}

	/** Trims, drops blanks, and de-duplicates while preserving order. */
	private static List<String> normalize(List<String> ids) {
		return new LinkedHashSet<>(ids.stream()
			.map(String::trim)
			.filter(id -> !id.isEmpty())
			.toList()).stream().toList();
	}

	private record CacheEntry(Instant fetchedAt, Optional<List<String>> value) {
	}
}
