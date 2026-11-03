package dev.pandesal.resume_reviewer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A stored ATS evaluation report for a resume, keyed by the resume file hash
 * (uniqueness regardless of file name). Holds the base64-encoded resume text,
 * the full markdown report, the overall score, the detailed per-category
 * ratings, and the three narrative sections produced by the LLM.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Review {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** SHA-256 hash of the resume file; enforces uniqueness regardless of file name. */
	@Column(nullable = false, unique = true, length = 64)
	private String fileHash;

	/** Base64-encoded extracted resume text. */
	@Column(nullable = false, length = Integer.MAX_VALUE)
	private String resumeTextBase64;

	/** Full markdown evaluation report. */
	@Column(nullable = false, length = Integer.MAX_VALUE)
	private String report;

	/** Overall ATS score (0-100). */
	@Column(nullable = false)
	private double overallScore;

	/** Detailed per-category ratings (markdown bullet list). */
	@Column(nullable = false, length = Integer.MAX_VALUE)
	private String detailedRatings;

	/** Critical ATS blockers (markdown bullet list). */
	@Column(length = Integer.MAX_VALUE)
	private String criticalBlockers;

	/** Strengths (markdown bullet list). */
	@Column(length = Integer.MAX_VALUE)
	private String strengths;

	/** Recommendations (markdown bullet list). */
	@Column(length = Integer.MAX_VALUE)
	private String recommendations;

	@Column(nullable = false)
	private Instant createdAt;
}
