package dev.pandesal.resume_reviewer.repository;

import dev.pandesal.resume_reviewer.domain.Review;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

	Optional<Review> findByFileHash(String fileHash);
}
