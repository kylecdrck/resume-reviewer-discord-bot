package dev.pandesal.resume_reviewer.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pandesal.resume_reviewer.domain.BotConfig;
import dev.pandesal.resume_reviewer.repository.BotConfigRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(BotConfigService.class)
class BotConfigServiceTest {

	@Autowired
	private BotConfigRepository repository;

	@Autowired
	private BotConfigService service;

	@TestConfiguration
	static class TestConfig {

		@Bean
		BotProperties botProperties() {
			// TTL of 0 forces a DB read on every call, so the cache is never "fresh".
			return new BotProperties(300, 0);
		}
	}

	@Test
	void defaultAllowedWhenUnconfigured() {
		assertThat(service.allowedChannels()).isEmpty();
		assertThat(service.isChannelAllowed("123")).isTrue();
	}

	@Test
	void saveAndReadRoundTrip() {
		service.saveAllowedChannels(List.of("111", "222"));
		assertThat(service.allowedChannels().get()).containsExactly("111", "222");
		assertThat(service.isChannelAllowed("111")).isTrue();
		assertThat(service.isChannelAllowed("222")).isTrue();
	}

	@Test
	void saveTrimsAndDedupes() {
		service.saveAllowedChannels(List.of(" 111 ", "111", "222", ""));
		assertThat(service.allowedChannels().get()).containsExactly("111", "222");
	}

	@Test
	void upsertReplacesPreviousValue() {
		service.saveAllowedChannels(List.of("111", "222"));
		service.saveAllowedChannels(List.of("333"));
		assertThat(service.allowedChannels().get()).containsExactly("333");
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	void emptyListClearsConfiguration() {
		service.saveAllowedChannels(List.of("111"));
		service.saveAllowedChannels(List.of());
		assertThat(service.allowedChannels()).isEmpty();
		assertThat(repository.count()).isZero();
		assertThat(service.isChannelAllowed("111")).isTrue();
	}

	@Test
	void disallowedChannelReturnsFalse() {
		service.saveAllowedChannels(List.of("111", "222"));
		assertThat(service.isChannelAllowed("999")).isFalse();
	}

		@Test
			void emptyConfiguredListAllowsAnyChannel() {
				service.saveAllowedChannels(List.of());
				assertThat(service.isChannelAllowed("999")).isTrue();
			}

	@Test
	void writeThroughRefreshesCacheImmediately() {
		service.saveAllowedChannels(List.of("111"));
		// Even with a 0s TTL the value is served from the just-written cache.
		assertThat(service.allowedChannels().get()).containsExactly("111");
	}

	@Test
	void cacheRenewsFromDbAfterTtlExpiry() {
		service.saveAllowedChannels(List.of("111"));
		// Mutate the row directly; the next read must renew from the DB.
		saveRow("222");
		assertThat(service.allowedChannels().get()).containsExactly("222");
	}

	private void saveRow(String value) {
		BotConfig config = new BotConfig();
		config.setConfigKey(BotConfigService.KEY_ALLOWED_CHANNELS);
		config.setConfigValue(value);
		config.setUpdatedAt(Instant.now());
		repository.save(config);
	}
}
