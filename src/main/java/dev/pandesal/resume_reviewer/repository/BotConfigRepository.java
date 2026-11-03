package dev.pandesal.resume_reviewer.repository;

import dev.pandesal.resume_reviewer.domain.BotConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotConfigRepository extends JpaRepository<BotConfig, String> {
}
